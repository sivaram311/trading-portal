import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { GannSnapshot, IctSnapshot, IctZone, OhlcBar } from '../../core/models';

type OverlayKind = 'ob' | 'fvg' | 'ote' | 'so9';

interface PlottedCandle {
  x: number;
  width: number;
  bull: boolean;
  bodyTop: number;
  bodyHeight: number;
  wickTop: number;
  wickBottom: number;
}

interface OverlayBand {
  top: number;
  height: number;
  kind: OverlayKind;
}

interface OverlayLine {
  y: number;
  kind: OverlayKind;
  label: string;
}

interface LegendItem {
  kind: OverlayKind;
  label: string;
  color: string;
}

const MAX_CANDLES = 60;
const OVERLAY_COLORS: Record<OverlayKind, string> = {
  ob: '#3d9a8b',
  fvg: '#5b9aa9',
  ote: '#9b7bb8',
  so9: '#c9922e'
};
const OVERLAY_LABELS: Record<OverlayKind, string> = {
  ob: 'Order block',
  fvg: 'FVG',
  ote: 'OTE',
  so9: 'So9'
};

/**
 * Dominant OHLC visual for the confluence page: pure-SVG candlesticks (no
 * chart-lib dependency) with optional ICT/Gann level overlays. Bars come from
 * `ApiService.getOhlc()` (real endpoint when reachable, deterministic mock
 * fallback otherwise) — this component stays presentation-only so it also
 * works with any `bars` array a parent passes in directly.
 */
@Component({
  selector: 'tp-candle-chart',
  standalone: true,
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="w-full">
      <div class="mb-1.5 flex items-center justify-between">
        <span class="font-mono text-[0.62rem] uppercase tracking-widest text-slate-500">XAUUSD · {{ tf() }}</span>
        @if (lastClose(); as lc) {
          <span class="font-mono text-[0.74rem] tabular" [style.color]="changePct() >= 0 ? '#37c99e' : '#f2617a'">
            {{ lc | number: '1.2-2' }}
            <span class="text-slate-500">({{ changePct() >= 0 ? '+' : '' }}{{ changePct() | number: '1.1-1' }}%)</span>
          </span>
        }
      </div>

      @if (candles().length) {
        <svg
          [attr.viewBox]="'0 0 ' + W + ' ' + H"
          preserveAspectRatio="xMidYMid meet"
          class="h-[30vh] max-h-[260px] min-h-[180px] w-full"
          role="img"
          [attr.aria-label]="ariaLabel()"
        >
          @for (band of overlayBands(); track band.kind + band.top) {
            <rect
              [attr.x]="padLeft"
              [attr.y]="band.top"
              [attr.width]="W - padLeft - padRight"
              [attr.height]="band.height"
              rx="2"
              [attr.fill]="bandFill(band.kind)"
              [attr.stroke]="bandStroke(band.kind)"
              stroke-dasharray="2 3"
            />
          }

          @for (line of overlayLines(); track line.kind + line.y) {
            <line
              [attr.x1]="padLeft"
              [attr.y1]="line.y"
              [attr.x2]="W - padRight"
              [attr.y2]="line.y"
              [attr.stroke]="OVERLAY_COLORS[line.kind]"
              stroke-width="1"
              stroke-dasharray="1 3"
              stroke-opacity="0.85"
            />
            <text
              [attr.x]="W - padRight - 2"
              [attr.y]="line.y - 3"
              text-anchor="end"
              class="font-mono"
              [attr.fill]="OVERLAY_COLORS[line.kind]"
              font-size="7.5"
            >
              {{ line.label }}
            </text>
          }

          @for (c of candles(); track $index) {
            <line
              [attr.x1]="c.x"
              [attr.y1]="c.wickTop"
              [attr.x2]="c.x"
              [attr.y2]="c.wickBottom"
              [attr.stroke]="c.bull ? '#37c99e' : '#f2617a'"
              stroke-width="1"
            />
            <rect
              [attr.x]="c.x - c.width / 2"
              [attr.y]="c.bodyTop"
              [attr.width]="c.width"
              [attr.height]="c.bodyHeight"
              rx="0.6"
              [attr.fill]="c.bull ? '#37c99e' : '#f2617a'"
            />
          }
        </svg>

        @if (legendItems().length) {
          <div class="mt-1.5 flex flex-wrap gap-x-3 gap-y-1">
            @for (item of legendItems(); track item.kind) {
              <span class="flex items-center gap-1 font-mono text-[0.6rem] text-slate-500">
                <span class="h-1.5 w-1.5 rounded-full" [style.background]="item.color"></span>{{ item.label }}
              </span>
            }
          </div>
        }
      } @else {
        <div class="flex h-[180px] w-full items-center justify-center rounded-lg border border-dashed border-obsidian-600">
          <p class="font-mono text-[0.7rem] text-slate-500">No bar data</p>
        </div>
      }
    </div>
  `
})
export class CandleChartComponent {
  readonly bars = input<OhlcBar[]>([]);
  readonly tf = input<string>('M15');
  readonly ict = input<IctSnapshot | null>(null);
  readonly gann = input<GannSnapshot | null>(null);
  readonly showOverlays = input<boolean>(true);

  protected readonly OVERLAY_COLORS = OVERLAY_COLORS;
  protected readonly W = 320;
  protected readonly H = 220;
  protected readonly padTop = 14;
  protected readonly padBottom = 20;
  protected readonly padLeft = 6;
  protected readonly padRight = 6;

  private visibleBars = computed(() => {
    const b = this.bars();
    return b.length > MAX_CANDLES ? b.slice(b.length - MAX_CANDLES) : b;
  });

  private overlayPrices = computed<number[]>(() => {
    if (!this.showOverlays()) return [];
    const prices: number[] = [];
    const ict = this.ict();
    for (const z of this.activeZones(ict)) prices.push(z.low, z.high);
    const ote = ict?.zones?.active_ote;
    if (ote) {
      if (ote.deep > 0) prices.push(ote.deep);
      if (ote.shallow > 0) prices.push(ote.shallow);
      if (ote.sweet > 0) prices.push(ote.sweet);
    }
    const so9 = this.resolveSo9(this.gann());
    if (so9 && so9.price > 0) prices.push(so9.price);
    return prices;
  });

  private range = computed(() => {
    const bars = this.visibleBars();
    if (!bars.length) return { min: 0, max: 1 };
    let min = Math.min(...bars.map((b) => b.low));
    let max = Math.max(...bars.map((b) => b.high));
    for (const p of this.overlayPrices()) {
      min = Math.min(min, p);
      max = Math.max(max, p);
    }
    if (min === max) {
      min -= 1;
      max += 1;
    }
    const pad = (max - min) * 0.08;
    return { min: min - pad, max: max + pad };
  });

  private yFor(price: number): number {
    const { min, max } = this.range();
    const usable = this.H - this.padTop - this.padBottom;
    const ratio = (price - min) / (max - min);
    return this.padTop + (1 - ratio) * usable;
  }

  protected candles = computed<PlottedCandle[]>(() => {
    const bars = this.visibleBars();
    const n = bars.length;
    if (!n) return [];
    const usableW = this.W - this.padLeft - this.padRight;
    const slot = usableW / n;
    const width = Math.max(1.5, Math.min(6, slot * 0.62));
    return bars.map((b, i) => {
      const x = this.padLeft + slot * i + slot / 2;
      const bull = b.close >= b.open;
      const yOpen = this.yFor(b.open);
      const yClose = this.yFor(b.close);
      return {
        x,
        width,
        bull,
        bodyTop: Math.min(yOpen, yClose),
        bodyHeight: Math.max(1, Math.abs(yClose - yOpen)),
        wickTop: this.yFor(b.high),
        wickBottom: this.yFor(b.low)
      };
    });
  });

  protected overlayBands = computed<OverlayBand[]>(() => {
    if (!this.showOverlays()) return [];
    const out: OverlayBand[] = [];
    const ict = this.ict();
    for (const z of this.activeZones(ict)) {
      const yHigh = this.yFor(z.high);
      const yLow = this.yFor(z.low);
      out.push({
        top: Math.min(yHigh, yLow),
        height: Math.max(3, Math.abs(yLow - yHigh)),
        kind: z.type === 'OB' ? 'ob' : 'fvg'
      });
    }
    const ote = ict?.zones?.active_ote;
    if (ote && ote.deep > 0 && ote.shallow > 0) {
      const yDeep = this.yFor(ote.deep);
      const yShallow = this.yFor(ote.shallow);
      out.push({ top: Math.min(yDeep, yShallow), height: Math.max(3, Math.abs(yShallow - yDeep)), kind: 'ote' });
    }
    return out;
  });

  protected overlayLines = computed<OverlayLine[]>(() => {
    if (!this.showOverlays()) return [];
    const out: OverlayLine[] = [];
    const so9 = this.resolveSo9(this.gann());
    if (so9 && so9.price > 0) {
      out.push({ y: this.yFor(so9.price), kind: 'so9', label: 'SO9 ' + so9.kind.toUpperCase() });
    }
    const ote = this.ict()?.zones?.active_ote;
    if (ote && ote.sweet > 0) {
      out.push({ y: this.yFor(ote.sweet), kind: 'ote', label: 'OTE' });
    }
    return out;
  });

  protected legendItems = computed<LegendItem[]>(() => {
    if (!this.showOverlays()) return [];
    const kinds = new Set<OverlayKind>();
    for (const b of this.overlayBands()) kinds.add(b.kind);
    for (const l of this.overlayLines()) kinds.add(l.kind);
    return Array.from(kinds).map((kind) => ({ kind, label: OVERLAY_LABELS[kind], color: OVERLAY_COLORS[kind] }));
  });

  protected lastClose = computed<number | null>(() => {
    const bars = this.visibleBars();
    return bars.length ? bars[bars.length - 1].close : null;
  });

  protected changePct = computed(() => {
    const bars = this.visibleBars();
    if (bars.length < 2) return 0;
    const first = bars[0].open;
    const last = bars[bars.length - 1].close;
    return first ? ((last - first) / first) * 100 : 0;
  });

  protected ariaLabel = computed(() => {
    const bars = this.visibleBars();
    if (!bars.length) return 'Candle chart: no data';
    const last = bars[bars.length - 1];
    return `Candle chart for XAUUSD ${this.tf()}, ${bars.length} bars, last close ${last.close.toFixed(2)}.`;
  });

  protected bandFill(kind: OverlayKind): string {
    const rgb: Record<OverlayKind, string> = {
      ob: '61,154,139',
      fvg: '91,154,169',
      ote: '155,123,184',
      so9: '201,146,46'
    };
    return `rgba(${rgb[kind]},0.12)`;
  }

  protected bandStroke(kind: OverlayKind): string {
    const rgb: Record<OverlayKind, string> = {
      ob: '61,154,139',
      fvg: '91,154,169',
      ote: '155,123,184',
      so9: '201,146,46'
    };
    return `rgba(${rgb[kind]},0.45)`;
  }

  private activeZones(ict: IctSnapshot | null): IctZone[] {
    if (!ict?.zones) return [];
    return [...(ict.zones.order_blocks ?? []), ...(ict.zones.fvgs ?? [])]
      .filter((z) => z.low > 0 && z.high > 0 && z.state !== 'filled')
      .slice(0, 4);
  }

  private resolveSo9(gann: GannSnapshot | null) {
    if (!gann?.so9) return null;
    const { at_level, nearest, levels } = gann.so9;
    if (at_level && levels?.length) {
      const at = levels.reduce((best, lv) => (!best || Math.abs(lv.dist) < Math.abs(best.dist) ? lv : best));
      if (at.price > 0) return at;
    }
    if (nearest && nearest.price > 0) return nearest;
    return null;
  }
}

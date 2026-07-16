import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ConfluenceDecision, GannSnapshot, IctSnapshot, IctZone } from '../../core/models';

type TradeKind = 'target' | 'entry' | 'stop' | 'now';
type OverlayKind = 'ote' | 'ob' | 'fvg' | 'breaker' | 'ifvg' | 'so9' | 'gann_1x1';
type LevelKind = TradeKind | OverlayKind;

interface PlottedLevel {
  y: number;
  price: number;
  label: string;
  kind: LevelKind;
  overlay: boolean;
}

interface OverlayCandidate {
  price: number;
  label: string;
  kind: OverlayKind;
  priority: number;
}

const MAX_OVERLAY_LINES = 8;
const PRICE_EPS = 0.35;

/**
 * Dominant first-viewport visual: a vertical price rail with the trade's
 * geometry (entry zone band, stop, targets, live mark) — deliberately NOT a
 * wall of stat cards. Pure SVG so it scales cleanly from 360px up.
 */
@Component({
  selector: 'tp-price-levels',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative w-full">
      <svg
        [attr.viewBox]="'0 0 320 ' + H"
        preserveAspectRatio="xMidYMid meet"
        class="h-[46vh] max-h-[420px] min-h-[280px] w-full"
        role="img"
        [attr.aria-label]="ariaLabel()"
      >
        <defs>
          <linearGradient id="railGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" [attr.stop-color]="isShort() ? '#f2617a' : '#37c99e'" stop-opacity="0.0" />
            <stop offset="50%" [attr.stop-color]="isShort() ? '#f2617a' : '#37c99e'" stop-opacity="0.16" />
            <stop offset="100%" [attr.stop-color]="isShort() ? '#f2617a' : '#37c99e'" stop-opacity="0.0" />
          </linearGradient>
        </defs>

        <!-- central rail -->
        <rect [attr.x]="railX - 5" y="10" width="10" [attr.height]="H - 20" rx="5" fill="url(#railGrad)" />
        <line [attr.x1]="railX" y1="12" [attr.x2]="railX" [attr.y2]="H - 12" stroke="#252c37" stroke-width="1.5" />

        <!-- entry zone band -->
        @if (entryBand(); as band) {
          <rect
            x="34"
            [attr.y]="band.top"
            [attr.width]="252"
            [attr.height]="band.height"
            rx="6"
            [attr.fill]="isShort() ? 'rgba(242,97,122,0.10)' : 'rgba(55,201,158,0.10)'"
            [attr.stroke]="isShort() ? 'rgba(242,97,122,0.45)' : 'rgba(55,201,158,0.45)'"
            stroke-dasharray="3 3"
          />
        }

        <!-- OTE band (deep ↔ shallow) -->
        @if (oteBand(); as band) {
          <rect
            x="34"
            [attr.y]="band.top"
            [attr.width]="252"
            [attr.height]="band.height"
            rx="4"
            fill="rgba(155,123,184,0.08)"
            stroke="rgba(155,123,184,0.35)"
            stroke-dasharray="2 4"
          />
        }

        <!-- levels -->
        @for (lv of levels(); track lv.label + lv.price) {
          <g>
            <line
              x1="34"
              [attr.y1]="lv.y"
              x2="286"
              [attr.y2]="lv.y"
              [attr.stroke]="strokeFor(lv.kind)"
              [attr.stroke-width]="lv.kind === 'now' ? 2 : lv.overlay ? 1 : 1"
              [attr.stroke-dasharray]="dashFor(lv)"
              [attr.stroke-opacity]="lv.overlay ? 0.82 : 1"
              [class.animate-pulseline]="lv.kind === 'now'"
            />
            <circle
              [attr.cx]="railX"
              [attr.cy]="lv.y"
              [attr.r]="lv.kind === 'now' ? 4.5 : lv.overlay ? 2.5 : 3"
              [attr.fill]="strokeFor(lv.kind)"
              [attr.fill-opacity]="lv.overlay ? 0.82 : 1"
            />
            <text
              x="40"
              [attr.y]="lv.y - 5"
              class="font-mono"
              [attr.fill]="labelColor(lv.kind, lv.overlay)"
              font-size="9.5"
              letter-spacing="1.5"
            >
              {{ lv.label }}
            </text>
            <text
              x="282"
              [attr.y]="lv.y - 5"
              text-anchor="end"
              class="font-mono tabular"
              [attr.fill]="strokeFor(lv.kind)"
              [attr.fill-opacity]="lv.overlay ? 0.82 : 1"
              font-size="11"
              font-weight="500"
            >
              {{ lv.price | number: '1.2-2' }}
            </text>
          </g>
        }
      </svg>
    </div>
  `,
  imports: [DecimalPipe]
})
export class PriceLevelsComponent {
  readonly decision = input.required<ConfluenceDecision>();
  /** Optional live/last price mark; falls back to entry midpoint when absent. */
  readonly nowPrice = input<number | null>(null);
  readonly ict = input<IctSnapshot | null>(null);
  readonly gann = input<GannSnapshot | null>(null);

  protected readonly H = 340;
  protected readonly railX = 160;
  private readonly padTop = 26;
  private readonly padBottom = 26;

  protected isShort = computed(() => this.decision().direction === 'short');

  private overlayPrices = computed(() =>
    this.selectOverlays().map((c) => c.price).filter((n) => n > 0)
  );

  private allPrices = computed<number[]>(() => {
    const d = this.decision();
    const now = this.nowPrice() ?? (d.entry.low + d.entry.high) / 2;
    return [d.entry.low, d.entry.high, d.stop, ...d.targets, now, ...this.overlayPrices()].filter((n) => n > 0);
  });

  private range = computed(() => {
    const ps = this.allPrices();
    let min = Math.min(...ps);
    let max = Math.max(...ps);
    if (min === max) {
      min -= 1;
      max += 1;
    }
    const pad = (max - min) * 0.12;
    return { min: min - pad, max: max + pad };
  });

  private yFor(price: number): number {
    const { min, max } = this.range();
    const usable = this.H - this.padTop - this.padBottom;
    const ratio = (price - min) / (max - min);
    return this.padTop + (1 - ratio) * usable;
  }

  protected entryBand = computed(() => {
    const d = this.decision();
    const yHigh = this.yFor(d.entry.high);
    const yLow = this.yFor(d.entry.low);
    return { top: Math.min(yHigh, yLow), height: Math.max(6, Math.abs(yLow - yHigh)) };
  });

  protected oteBand = computed(() => {
    const ote = this.ict()?.zones.active_ote;
    if (!ote || ote.deep <= 0 || ote.shallow <= 0) return null;
    const yDeep = this.yFor(ote.deep);
    const yShallow = this.yFor(ote.shallow);
    return { top: Math.min(yDeep, yShallow), height: Math.max(4, Math.abs(yShallow - yDeep)) };
  });

  protected levels = computed<PlottedLevel[]>(() => {
    const d = this.decision();
    const now = this.nowPrice() ?? (d.entry.low + d.entry.high) / 2;
    const out: PlottedLevel[] = [];
    d.targets.forEach((t, i) =>
      out.push({ price: t, y: this.yFor(t), label: 'T' + (i + 1), kind: 'target', overlay: false })
    );
    out.push({
      price: d.entry.high,
      y: this.yFor(d.entry.high),
      label: 'ENTRY ' + d.entry.type,
      kind: 'entry',
      overlay: false
    });
    out.push({ price: d.stop, y: this.yFor(d.stop), label: 'STOP', kind: 'stop', overlay: false });
    out.push({ price: now, y: this.yFor(now), label: 'NOW', kind: 'now', overlay: false });

    for (const c of this.selectOverlays()) {
      out.push({
        price: c.price,
        y: this.yFor(c.price),
        label: c.label,
        kind: c.kind,
        overlay: true
      });
    }
    return out;
  });

  protected strokeFor(kind: LevelKind): string {
    switch (kind) {
      case 'target':
        return '#eec25a';
      case 'entry':
        return this.isShort() ? '#f2617a' : '#37c99e';
      case 'stop':
        return '#8b93a7';
      case 'now':
        return '#f6f7fb';
      case 'ote':
        return '#9b7bb8';
      case 'ob':
        return '#3d9a8b';
      case 'fvg':
        return '#5b9aa9';
      case 'breaker':
        return '#c76b7a';
      case 'ifvg':
        return '#7eb8c9';
      case 'so9':
        return '#c9922e';
      case 'gann_1x1':
        return '#708090';
    }
  }

  protected dashFor(lv: PlottedLevel): string {
    if (lv.kind === 'now') return '';
    if (lv.kind === 'stop') return '5 4';
    if (lv.overlay) return '1 4';
    return '2 5';
  }

  protected labelColor(kind: LevelKind, overlay: boolean): string {
    if (kind === 'now') return '#c7ccd8';
    if (overlay) return '#8a8294';
    return '#7c8496';
  }

  protected ariaLabel = computed(() => {
    const d = this.decision();
    const extras = this.selectOverlays().map((c) => `${c.label} ${c.price.toFixed(2)}`);
    const overlayPart = extras.length ? ` Overlays: ${extras.join(', ')}.` : '';
    return `Price levels for ${d.direction} setup: entry ${d.entry.low}-${d.entry.high}, stop ${d.stop}, targets ${d.targets.join(', ')}.${overlayPart}`;
  });

  private selectOverlays(): OverlayCandidate[] {
    const raw: OverlayCandidate[] = [];
    const gann = this.gann();
    const ict = this.ict();

    const eq = gann?.angle?.equilibrium;
    if (eq != null && eq > 0) {
      raw.push({ price: eq, label: '1×1 EQ', kind: 'gann_1x1', priority: 1 });
    }

    const so9Level = this.resolveSo9(gann);
    if (so9Level) {
      raw.push({
        price: so9Level.price,
        label: 'SO9 ' + so9Level.kind.toUpperCase(),
        kind: 'so9',
        priority: 2
      });
    }

    const ote = ict?.zones?.active_ote;
    if (ote && ote.sweet > 0) {
      raw.push({ price: ote.sweet, label: 'OTE SWEET', kind: 'ote', priority: 3 });
      if (ote.deep > 0) raw.push({ price: ote.deep, label: 'OTE DEEP', kind: 'ote', priority: 5 });
      if (ote.shallow > 0) raw.push({ price: ote.shallow, label: 'OTE SHL', kind: 'ote', priority: 6 });
    }

    const active = ict?.zones?.active_entry;
    if (active && active.low > 0 && active.high > 0) {
      raw.push({
        price: (active.low + active.high) / 2,
        label: active.type,
        kind: this.zoneKind(active.type),
        priority: 4
      });
    }

    for (const z of this.activeZones(ict)) {
      if (active && z.low === active.low && z.high === active.high && z.type === active.type) continue;
      raw.push({
        price: (z.low + z.high) / 2,
        label: z.type,
        kind: this.zoneKind(z.type),
        priority: 7
      });
    }

    raw.sort((a, b) => a.priority - b.priority || a.price - b.price);
    const picked: OverlayCandidate[] = [];
    for (const c of raw) {
      if (picked.some((p) => Math.abs(p.price - c.price) < PRICE_EPS)) continue;
      picked.push(c);
      if (picked.length >= MAX_OVERLAY_LINES) break;
    }
    return picked;
  }

  private resolveSo9(gann: GannSnapshot | null) {
    if (!gann?.so9) return null;
    const { at_level, nearest, levels } = gann.so9;
    if (at_level && levels?.length) {
      const at = levels.reduce((best, lv) =>
        !best || Math.abs(lv.dist) < Math.abs(best.dist) ? lv : best
      );
      if (at.price > 0) return at;
    }
    if (nearest && nearest.price > 0) return nearest;
    if (levels?.length) {
      const closest = levels.reduce((best, lv) =>
        !best || Math.abs(lv.dist) < Math.abs(best.dist) ? lv : best
      );
      if (closest.price > 0) return closest;
    }
    return null;
  }

  private activeZones(ict: IctSnapshot | null): IctZone[] {
    if (!ict?.zones) return [];
    const zones = [...(ict.zones.order_blocks ?? []), ...(ict.zones.fvgs ?? [])];
    return zones.filter(
      (z) =>
        z.low > 0 &&
        z.high > 0 &&
        z.state !== 'filled' &&
        ['OB', 'FVG', 'BREAKER', 'IFVG'].includes(z.type)
    );
  }

  private zoneKind(type: string): OverlayKind {
    switch (type) {
      case 'OB':
        return 'ob';
      case 'FVG':
        return 'fvg';
      case 'BREAKER':
        return 'breaker';
      case 'IFVG':
        return 'ifvg';
      default:
        return 'ob';
    }
  }
}

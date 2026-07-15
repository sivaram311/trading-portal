import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ConfluenceDecision } from '../../core/models';

interface PlottedLevel {
  y: number;
  price: number;
  label: string;
  kind: 'target' | 'entry' | 'stop' | 'now';
}

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

        <!-- levels -->
        @for (lv of levels(); track lv.label) {
          <g>
            <line
              x1="34"
              [attr.y1]="lv.y"
              x2="286"
              [attr.y2]="lv.y"
              [attr.stroke]="strokeFor(lv.kind)"
              [attr.stroke-width]="lv.kind === 'now' ? 2 : 1"
              [attr.stroke-dasharray]="lv.kind === 'now' ? '' : lv.kind === 'stop' ? '5 4' : '2 5'"
              [class.animate-pulseline]="lv.kind === 'now'"
            />
            <circle [attr.cx]="railX" [attr.cy]="lv.y" [attr.r]="lv.kind === 'now' ? 4.5 : 3" [attr.fill]="strokeFor(lv.kind)" />
            <text
              x="40"
              [attr.y]="lv.y - 5"
              class="font-mono"
              [attr.fill]="labelColor(lv.kind)"
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

  protected readonly H = 340;
  protected readonly railX = 160;
  private readonly padTop = 26;
  private readonly padBottom = 26;

  protected isShort = computed(() => this.decision().direction === 'short');

  private allPrices = computed<number[]>(() => {
    const d = this.decision();
    const now = this.nowPrice() ?? (d.entry.low + d.entry.high) / 2;
    return [d.entry.low, d.entry.high, d.stop, ...d.targets, now].filter((n) => n > 0);
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
    // higher price = higher on screen (smaller y)
    return this.padTop + (1 - ratio) * usable;
  }

  protected entryBand = computed(() => {
    const d = this.decision();
    const yHigh = this.yFor(d.entry.high);
    const yLow = this.yFor(d.entry.low);
    return { top: Math.min(yHigh, yLow), height: Math.max(6, Math.abs(yLow - yHigh)) };
  });

  protected levels = computed<PlottedLevel[]>(() => {
    const d = this.decision();
    const now = this.nowPrice() ?? (d.entry.low + d.entry.high) / 2;
    const out: PlottedLevel[] = [];
    d.targets.forEach((t, i) => out.push({ price: t, y: this.yFor(t), label: 'T' + (i + 1), kind: 'target' }));
    out.push({ price: d.entry.high, y: this.yFor(d.entry.high), label: 'ENTRY ' + d.entry.type, kind: 'entry' });
    out.push({ price: d.stop, y: this.yFor(d.stop), label: 'STOP', kind: 'stop' });
    out.push({ price: now, y: this.yFor(now), label: 'NOW', kind: 'now' });
    return out;
  });

  protected strokeFor(kind: PlottedLevel['kind']): string {
    switch (kind) {
      case 'target':
        return '#eec25a';
      case 'entry':
        return this.isShort() ? '#f2617a' : '#37c99e';
      case 'stop':
        return '#8b93a7';
      case 'now':
        return '#f6f7fb';
    }
  }

  protected labelColor(kind: PlottedLevel['kind']): string {
    return kind === 'now' ? '#c7ccd8' : '#7c8496';
  }

  protected ariaLabel = computed(() => {
    const d = this.decision();
    return `Price levels for ${d.direction} setup: entry ${d.entry.low}-${d.entry.high}, stop ${d.stop}, targets ${d.targets.join(', ')}.`;
  });
}

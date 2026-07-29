import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { TradingStyle } from '../../core/models';

const STYLES: readonly TradingStyle[] = ['SCALP', 'DAY', 'POSITIONAL'];

/**
 * Segmented SCALP/DAY/POSITIONAL picker. Reads/writes GET+PUT /api/style
 * (sibling backend endpoint may not exist yet — every call soft-fails) and
 * falls back to displaying the ops-reported `tradingStyle` via `[opsStyle]`.
 */
@Component({
  selector: 'tp-style-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
      <div
        role="radiogroup"
        aria-label="Trading style"
        class="inline-grid grid-cols-3 gap-0.5 rounded-xl border border-obsidian-600 bg-obsidian-800 p-0.5"
      >
        @for (s of styles; track s) {
          <button
            type="button"
            role="radio"
            [attr.aria-checked]="display() === s"
            [disabled]="saving()"
            (click)="select(s)"
            class="rounded-[0.6rem] px-2.5 py-1.5 font-mono text-[0.62rem] font-600 uppercase tracking-widest transition disabled:cursor-not-allowed disabled:opacity-50"
            [class.bg-gold-500]="display() === s"
            [class.text-obsidian-950]="display() === s"
            [class.text-slate-400]="display() !== s"
            [class.hover:text-gold-300]="display() !== s"
          >
            {{ s }}
          </button>
        }
      </div>
      @if (fromOps() && !loading()) {
        <span
          class="font-mono text-[0.58rem] uppercase tracking-widest text-slate-500"
          title="Backend /api/style unavailable — showing ops-reported style"
        >
          ops
        </span>
      }
    </div>
    @if (error(); as e) {
      <p class="mt-1.5 font-mono text-[0.62rem] leading-snug text-bear">{{ e }}</p>
    }
  `
})
export class StyleSelectorComponent implements OnInit {
  private readonly api = inject(ApiService);

  /** Fallback label sourced from /api/ops/status `tradingStyle` when /api/style is unavailable. */
  readonly opsStyle = input<string | null>(null);
  readonly styleChanged = output<TradingStyle>();

  readonly styles = STYLES;
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly fromOps = signal(false);

  private readonly current = signal<TradingStyle | null>(null);

  readonly display = computed<TradingStyle>(() => this.current() ?? this.asStyle(this.opsStyle()) ?? 'DAY');

  ngOnInit(): void {
    this.api.getStyle().subscribe((info) => {
      this.loading.set(false);
      if (info?.style) {
        this.current.set(info.style);
        this.fromOps.set(false);
      } else {
        this.fromOps.set(true);
      }
    });
  }

  select(style: TradingStyle): void {
    if (style === this.display() || this.saving()) return;
    const previous = this.current();
    const previousFromOps = this.fromOps();
    this.saving.set(true);
    this.error.set(null);
    this.current.set(style);
    this.fromOps.set(false);
    this.api.putStyle(style).subscribe((info) => {
      this.saving.set(false);
      if (info?.style) {
        this.current.set(info.style);
        this.styleChanged.emit(info.style);
      } else {
        this.current.set(previous);
        this.fromOps.set(previousFromOps);
        this.error.set('Could not save style — /api/style unavailable.');
      }
    });
  }

  private asStyle(v: string | null): TradingStyle | null {
    return v === 'SCALP' || v === 'DAY' || v === 'POSITIONAL' ? v : null;
  }
}

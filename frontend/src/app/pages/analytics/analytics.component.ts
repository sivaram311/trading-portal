import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AnalyticsSummary, SessionBreakdownRow } from '../../core/models';

@Component({
  selector: 'tp-analytics',
  standalone: true,
  imports: [RouterLink, DecimalPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="mx-auto flex min-h-screen w-full max-w-md flex-col px-4 pb-12 pt-4">
      <header class="flex items-center justify-between">
        <div>
          <p class="font-mono text-[0.62rem] uppercase tracking-[0.38em] text-gold-400/80">Performance</p>
          <h1 class="font-display text-xl font-600 leading-none text-slate-50">Analytics</h1>
        </div>
        <a
          routerLink="/journal"
          class="font-mono text-[0.66rem] uppercase tracking-widest text-slate-500 transition hover:text-gold-300"
        >
          ← journal
        </a>
      </header>

      @if (banner(); as b) {
        <div class="mt-3 rounded-lg border border-gold-500/40 bg-gold-500/10 px-3 py-2 text-[0.74rem] leading-snug text-gold-300">
          {{ b }}
        </div>
      }

      @if (loading()) {
        <div class="flex flex-1 items-center justify-center">
          <p class="font-mono text-sm text-slate-500">Crunching paper trades…</p>
        </div>
      } @else if (!summary() || summary()!.trade_count === 0) {
        <div class="flex flex-1 flex-col items-center justify-center gap-2 text-center">
          <p class="font-display text-lg text-slate-300">No trades yet</p>
          <p class="max-w-[22rem] font-mono text-[0.76rem] text-slate-500">
            Confirm a paper trade from the live confluence page to start building performance stats.
          </p>
          <a routerLink="/" class="mt-2 text-gold-300 underline">Go to live confluence</a>
        </div>
      } @else {
        @let s = summary()!;

        <!-- headline KPIs -->
        <section class="mt-6 grid grid-cols-3 gap-2.5">
          <div class="rounded-xl border border-obsidian-700/80 bg-obsidian-800/60 p-3 text-center">
            <p class="font-mono text-[0.58rem] uppercase tracking-widest text-slate-500">Win rate</p>
            <p class="mt-1 font-display text-2xl font-700 text-slate-50">{{ s.win_rate * 100 | number: '1.0-1' }}%</p>
          </div>
          <div class="rounded-xl border border-obsidian-700/80 bg-obsidian-800/60 p-3 text-center">
            <p class="font-mono text-[0.58rem] uppercase tracking-widest text-slate-500">Expectancy R</p>
            <p
              class="mt-1 font-display text-2xl font-700"
              [class.text-bull]="s.expectancy_r > 0"
              [class.text-bear]="s.expectancy_r < 0"
              [class.text-slate-50]="s.expectancy_r === 0"
            >
              {{ s.expectancy_r | number: '1.2-2' }}
            </p>
          </div>
          <div class="rounded-xl border border-obsidian-700/80 bg-obsidian-800/60 p-3 text-center">
            <p class="font-mono text-[0.58rem] uppercase tracking-widest text-slate-500">Trades</p>
            <p class="mt-1 font-display text-2xl font-700 text-slate-50 tabular">{{ s.trade_count }}</p>
          </div>
        </section>

        <!-- secondary stats -->
        <dl class="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 rounded-xl border border-obsidian-700/70 bg-obsidian-800/40 p-3.5 font-mono text-[0.78rem]">
          <div>
            <dt class="text-slate-500">Profit factor</dt>
            <dd class="tabular text-slate-100">{{ s.profit_factor != null ? (s.profit_factor | number: '1.2-2') : '—' }}</dd>
          </div>
          <div>
            <dt class="text-slate-500">Total R</dt>
            <dd class="tabular" [class.text-bull]="(s.total_r ?? 0) > 0" [class.text-bear]="(s.total_r ?? 0) < 0">
              {{ s.total_r != null ? (s.total_r | number: '1.2-2') : '—' }}
            </dd>
          </div>
          <div>
            <dt class="text-slate-500">Avg win R</dt>
            <dd class="tabular text-bull">{{ s.avg_win_r != null ? (s.avg_win_r | number: '1.2-2') : '—' }}</dd>
          </div>
          <div>
            <dt class="text-slate-500">Avg loss R</dt>
            <dd class="tabular text-bear">{{ s.avg_loss_r != null ? (s.avg_loss_r | number: '1.2-2') : '—' }}</dd>
          </div>
          @if (s.as_of) {
            <div class="col-span-2">
              <dt class="text-slate-500">As of</dt>
              <dd class="text-slate-300">{{ s.as_of | date: 'medium' }}</dd>
            </div>
          }
        </dl>

        <!-- session breakdown -->
        <section class="mt-6">
          <h3 class="font-mono text-[0.66rem] uppercase tracking-widest text-slate-500">By session</h3>
          @if (sessions().length === 0) {
            <p class="mt-2 font-mono text-[0.76rem] text-slate-500">No session breakdown available.</p>
          } @else {
            <ul class="mt-2 space-y-2">
              @for (row of sessions(); track row.session) {
                <li class="rounded-xl border border-obsidian-700/80 bg-obsidian-800/60 p-3">
                  <div class="flex items-center justify-between">
                    <span class="font-display text-base font-600 text-slate-100">{{ sessionLabel(row.session) }}</span>
                    <span class="font-mono text-[0.68rem] text-slate-500">{{ row.trade_count }} trades</span>
                  </div>
                  <div class="mt-2 grid grid-cols-2 gap-2 font-mono text-[0.74rem] text-slate-400">
                    <div>
                      <span class="block text-[0.58rem] uppercase text-slate-600">Win rate</span>
                      <span class="tabular">{{ row.win_rate * 100 | number: '1.0-1' }}%</span>
                    </div>
                    <div>
                      <span class="block text-[0.58rem] uppercase text-slate-600">Expectancy R</span>
                      <span class="tabular" [class.text-bull]="row.expectancy_r > 0" [class.text-bear]="row.expectancy_r < 0">
                        {{ row.expectancy_r | number: '1.2-2' }}
                      </span>
                    </div>
                  </div>
                </li>
              }
            </ul>
          }
        </section>

        <!-- optional export -->
        <section class="mt-6">
          <button
            (click)="exportCsv()"
            [disabled]="exporting()"
            class="w-full rounded-xl border border-gold-500/40 bg-gold-500/10 px-3 py-3 text-center font-mono text-[0.72rem] uppercase tracking-widest text-gold-300 transition hover:border-gold-500/70 disabled:cursor-not-allowed disabled:opacity-40"
          >
            {{ exporting() ? 'Preparing export…' : 'Export backtest CSV' }}
          </button>
          @if (exportError(); as e) {
            <p class="mt-2 text-center text-[0.72rem] text-bear">{{ e }}</p>
          }
        </section>
      }
    </main>
  `
})
export class AnalyticsComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly summary = signal<AnalyticsSummary | null>(null);
  readonly sessions = signal<SessionBreakdownRow[]>([]);
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly exportError = signal<string | null>(null);

  readonly banner = computed(() =>
    this.api.usingMock() ? 'Backend unreachable — showing mock analytics fixtures.' : null
  );

  ngOnInit(): void {
    forkJoin({
      summary: this.api.getAnalyticsSummary(),
      bySession: this.api.getAnalyticsBySession()
    }).subscribe({
      next: ({ summary, bySession }) => {
        this.summary.set(summary);
        this.sessions.set(bySession.sessions ?? []);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  sessionLabel(session: string): string {
    return session.charAt(0).toUpperCase() + session.slice(1).toLowerCase();
  }

  exportCsv(): void {
    this.exporting.set(true);
    this.exportError.set(null);
    this.api.exportBacktestCsv().subscribe({
      next: (csv) => {
        this.exporting.set(false);
        if (!csv) {
          this.exportError.set('No backtest CSV available yet.');
          return;
        }
        this.downloadCsv(csv);
      },
      error: () => {
        this.exporting.set(false);
        this.exportError.set('Export failed — backtest engine unreachable.');
      }
    });
  }

  private downloadCsv(csv: string): void {
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `backtest-trades-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

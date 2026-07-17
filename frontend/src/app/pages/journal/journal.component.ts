import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Direction, Grade, JournalStatus, PaperJournalEntry } from '../../core/models';

@Component({
  selector: 'tp-journal',
  standalone: true,
  imports: [RouterLink, DecimalPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="mx-auto flex min-h-screen w-full max-w-md flex-col px-4 pb-12 pt-4">
      <header class="flex items-center justify-between">
        <div>
          <p class="font-mono text-[0.62rem] uppercase tracking-[0.38em] text-gold-400/80">Paper journal</p>
          <h1 class="font-display text-xl font-600 leading-none text-slate-50">Trading Portal</h1>
        </div>
        <a
          routerLink="/"
          class="font-mono text-[0.66rem] uppercase tracking-widest text-slate-500 transition hover:text-gold-300"
        >
          ← live
        </a>
      </header>

      @if (banner(); as b) {
        <div class="mt-3 rounded-lg border border-gold-500/40 bg-gold-500/10 px-3 py-2 text-[0.74rem] text-gold-300">
          {{ b }}
        </div>
      }

      <!-- lightweight filters -->
      <div class="mt-4 flex gap-2 overflow-x-auto pb-1">
        @for (f of gradeFilters; track f) {
          <button
            (click)="setGrade(f)"
            class="shrink-0 rounded-full border px-3 py-1 font-mono text-[0.7rem] transition"
            [class.border-gold-500]="grade() === f"
            [class.text-gold-300]="grade() === f"
            [class.border-obsidian-600]="grade() !== f"
            [class.text-slate-400]="grade() !== f"
          >
            {{ f === '' ? 'all' : f }}
          </button>
        }
      </div>

      @if (loading()) {
        <p class="mt-8 text-center font-mono text-sm text-slate-500">Loading journal…</p>
      } @else if (items().length === 0) {
        <p class="mt-8 text-center font-mono text-sm text-slate-500">No journal entries yet.</p>
      } @else {
        <ul class="mt-4 space-y-2.5">
          @for (e of items(); track e.id) {
            <li class="rounded-xl border border-obsidian-700/80 bg-obsidian-800/60 p-3.5">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-2.5">
                  <span
                    class="rounded px-1.5 py-0.5 font-mono text-[0.72rem] font-700"
                    [style.color]="gradeColor(e.grade)"
                    [style.background]="gradeBg(e.grade)"
                  >{{ e.grade }}</span>
                  <span class="font-display text-lg font-600" [style.color]="dirColor(e.direction)">
                    {{ dirWord(e.direction) }}
                  </span>
                  <span class="font-mono text-xs text-slate-500">{{ e.mode }}</span>
                </div>
                <span
                  class="rounded-full px-2 py-0.5 font-mono text-[0.64rem] uppercase tracking-wide"
                  [style.color]="statusColor(e.status)"
                  [style.background]="statusBg(e.status)"
                >{{ e.status }}</span>
              </div>

              <div class="mt-2 flex flex-wrap gap-1">
                @for (r of e.reasons.slice(0, 4); track r) {
                  <span class="rounded-full border border-obsidian-600 px-2 py-0.5 font-mono text-[0.62rem] text-slate-400">{{ r }}</span>
                }
              </div>

              <div class="mt-2.5 grid grid-cols-4 gap-2 font-mono text-[0.72rem] tabular text-slate-400">
                <div><span class="block text-[0.6rem] uppercase text-slate-600">Entry</span>{{ midEntry(e) | number: '1.2-2' }}</div>
                <div><span class="block text-[0.6rem] uppercase text-slate-600">Stop</span>{{ e.stop | number: '1.2-2' }}</div>
                <div><span class="block text-[0.6rem] uppercase text-slate-600">Score</span>{{ e.score | number: '1.1-1' }}</div>
                <div>
                  <span class="block text-[0.6rem] uppercase text-slate-600">R</span>
                  <span [class.text-bull]="(e.paper?.r_multiple ?? 0) > 0" [class.text-bear]="(e.paper?.r_multiple ?? 0) < 0">
                    {{ e.paper?.r_multiple != null ? (e.paper?.r_multiple | number: '1.2-2') : '—' }}
                  </span>
                </div>
              </div>
              @if (e.paper?.exit_reason || e.paper?.mfe_r != null || e.paper?.mae_r != null) {
                <div class="mt-2 grid grid-cols-3 gap-2 font-mono text-[0.72rem] tabular text-slate-400" data-testid="journal-lifecycle">
                  <div><span class="block text-[0.6rem] uppercase text-slate-600">Exit</span>{{ e.paper?.exit_reason || '—' }}</div>
                  <div><span class="block text-[0.6rem] uppercase text-slate-600">MFE R</span>{{ e.paper?.mfe_r != null ? (e.paper?.mfe_r | number: '1.2-2') : '—' }}</div>
                  <div><span class="block text-[0.6rem] uppercase text-slate-600">MAE R</span>{{ e.paper?.mae_r != null ? (e.paper?.mae_r | number: '1.2-2') : '—' }}</div>
                </div>
              }

              @if (e.action_note) {
                <p class="mt-2 text-[0.76rem] italic text-slate-500">“{{ e.action_note }}”</p>
              }
              <p class="mt-2 font-mono text-[0.62rem] text-slate-600">
                {{ e.session_date }} · {{ e.detected_at | date: 'shortTime' }} · {{ e.weights_version }}
              </p>
            </li>
          }
        </ul>
        <p class="mt-4 text-center font-mono text-[0.66rem] text-slate-600">{{ total() }} entries</p>
      }
    </main>
  `
})
export class JournalComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly items = signal<PaperJournalEntry[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly grade = signal<string>('');

  readonly gradeFilters = ['', 'A+', 'A', 'B', 'C', 'F'];

  readonly banner = computed(() =>
    this.api.usingMock() ? 'Backend unreachable — showing mock journal fixtures.' : null
  );

  ngOnInit(): void {
    this.load();
  }

  setGrade(g: string): void {
    this.grade.set(g);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api.listJournal({ grade: this.grade() || undefined, limit: 100 }).subscribe({
      next: (res) => {
        this.items.set(res.items);
        this.total.set(res.total);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  midEntry(e: PaperJournalEntry): number {
    return (e.entry.low + e.entry.high) / 2;
  }
  dirWord(d: Direction): string {
    return d === 'long' ? 'LONG' : d === 'short' ? 'SHORT' : 'FLAT';
  }
  dirColor(d: Direction): string {
    return d === 'long' ? '#37c99e' : d === 'short' ? '#f2617a' : '#8b93a7';
  }
  gradeColor(g: Grade): string {
    return g === 'F' ? '#f2617a' : '#07080a';
  }
  gradeBg(g: Grade): string {
    if (g === 'A+' || g === 'A') return '#eec25a';
    if (g === 'B') return '#d9a441';
    if (g === 'C') return '#8b93a7';
    return 'rgba(242,97,122,0.16)';
  }
  statusColor(s: JournalStatus): string {
    if (s === 'PAPER_OPEN' || s === 'PAPER_CLOSED') return '#37c99e';
    if (s === 'REJECTED') return '#f2617a';
    if (s === 'DISMISSED') return '#8b93a7';
    return '#eec25a';
  }
  statusBg(s: JournalStatus): string {
    if (s === 'PAPER_OPEN' || s === 'PAPER_CLOSED') return 'rgba(55,201,158,0.12)';
    if (s === 'REJECTED') return 'rgba(242,97,122,0.12)';
    if (s === 'DISMISSED') return 'rgba(139,147,167,0.12)';
    return 'rgba(238,194,90,0.12)';
  }
}

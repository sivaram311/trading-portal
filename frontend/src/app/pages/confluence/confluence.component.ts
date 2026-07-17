import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ConfluenceDecision, Direction, GannSnapshot, Grade, IctSnapshot } from '../../core/models';
import { PriceLevelsComponent } from '../../components/price-levels/price-levels.component';

@Component({
  selector: 'tp-confluence',
  standalone: true,
  imports: [PriceLevelsComponent, RouterLink, DecimalPipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="mx-auto flex min-h-screen w-full max-w-md flex-col px-4 pb-10 pt-4">
      <!-- brand -->
      <header class="flex items-center justify-between">
        <div>
          <p class="font-mono text-[0.62rem] uppercase tracking-[0.38em] text-gold-400/80">
            XAUUSD · Paper · {{ styleLabel() }}
          </p>
          <h1 class="font-display text-xl font-600 leading-none text-slate-50">Trading Portal</h1>
        </div>
        <button
          (click)="logout()"
          class="font-mono text-[0.66rem] uppercase tracking-widest text-slate-500 transition hover:text-gold-300"
        >
          {{ user() }} · exit
        </button>
      </header>

      @if (banner(); as b) {
        <div class="mt-3 rounded-lg border border-gold-500/40 bg-gold-500/10 px-3 py-2 text-[0.74rem] leading-snug text-gold-300">
          {{ b }}
        </div>
      }

      @if (loading()) {
        <div class="flex flex-1 items-center justify-center">
          <p class="font-mono text-sm text-slate-500">Reading confluence…</p>
        </div>
      } @else if (decision()) {
        @let d = decision()!;
        <!-- headline: grade + direction + mode -->
        <section class="mt-6">
          <div class="flex items-baseline gap-3">
            <span
              class="rounded-md px-2.5 py-1 font-mono text-sm font-700 tracking-wide"
              [style.color]="gradeColor(d.grade)"
              [style.background]="gradeBg(d.grade)"
            >
              {{ d.grade }}
            </span>
            <h2 class="font-display text-4xl font-700 leading-none" [style.color]="dirColor(d.direction)">
              {{ dirWord(d.direction) }}
            </h2>
            <span class="font-mono text-lg text-slate-400">· {{ modeWord(d.mode) }}</span>
          </div>
          <!-- one supporting reason line -->
          <p class="mt-3 text-[0.95rem] leading-snug text-slate-300">{{ headlineReason() }}</p>
        </section>

        <!-- dominant visual -->
        <section class="mt-2 flex-1" data-testid="price-levels">
          <tp-price-levels [decision]="d" [ict]="ict()" [gann]="gann()" />
        </section>

        @if (engineTags().length) {
          <div class="mt-2 flex flex-wrap gap-1.5" data-testid="engine-tags">
            @for (t of engineTags(); track t) {
              <span class="rounded-full border border-obsidian-600 px-2 py-0.5 font-mono text-[0.62rem] text-slate-400">{{ t }}</span>
            }
          </div>
        }

        <!-- CTA row -->
        <section class="mt-3 grid grid-cols-3 gap-2.5">
          <button
            (click)="confirm(d)"
            [disabled]="!canConfirm(d) || acting()"
            class="col-span-1 rounded-xl px-3 py-3.5 text-center font-600 text-obsidian-950 shadow-glow-bull transition disabled:cursor-not-allowed disabled:opacity-40"
            [style.background]="canConfirm(d) ? '#37c99e' : '#252c37'"
            [class.text-slate-400]="!canConfirm(d)"
          >
            <span class="block text-[0.7rem] uppercase tracking-widest opacity-80">Confirm</span>
            <span class="block text-sm">Paper</span>
          </button>
          <button
            (click)="dismiss(d)"
            [disabled]="acting()"
            class="col-span-1 rounded-xl border border-obsidian-600 bg-obsidian-800 px-3 py-3.5 text-center text-slate-300 transition hover:border-bear/60 hover:text-bear disabled:opacity-40"
          >
            <span class="block text-[0.7rem] uppercase tracking-widest opacity-70">Dismiss</span>
            <span class="block text-sm">Skip</span>
          </button>
          <a
            routerLink="/journal"
            class="col-span-1 flex flex-col items-center justify-center rounded-xl border border-obsidian-600 bg-obsidian-800 px-3 py-3.5 text-center text-slate-300 transition hover:border-gold-500/60 hover:text-gold-300"
          >
            <span class="block text-[0.7rem] uppercase tracking-widest opacity-70">Open</span>
            <span class="block text-sm">Journal</span>
          </a>
        </section>

        @if (actionMsg(); as m) {
          <p class="mt-3 rounded-lg border px-3 py-2 text-sm"
             [class.border-bull]="!actionErr()" [class.text-bull]="!actionErr()"
             [class.border-bear]="actionErr()" [class.text-bear]="actionErr()">
            {{ m }}
          </p>
        }

        <!-- detail panels scroll below the fold -->
        <section class="mt-8 space-y-5 border-t border-obsidian-700/70 pt-6">
          <div>
            <h3 class="font-mono text-[0.66rem] uppercase tracking-widest text-slate-500">Reasons</h3>
            <div class="mt-2 flex flex-wrap gap-1.5">
              @for (r of d.reasons; track r) {
                <span
                  class="rounded-full border px-2.5 py-1 font-mono text-[0.68rem]"
                  [class.border-gold-500]="r.startsWith('GANN_')"
                  [class.text-gold-300]="r.startsWith('GANN_')"
                  [class.border-bull]="r.startsWith('ICT_')"
                  [class.text-bull]="r.startsWith('ICT_')"
                  [class.border-obsidian-600]="!r.startsWith('GANN_') && !r.startsWith('ICT_')"
                  [class.text-slate-400]="!r.startsWith('GANN_') && !r.startsWith('ICT_')"
                >
                  {{ r }}
                </span>
              }
            </div>
          </div>

          <div>
            <h3 class="font-mono text-[0.66rem] uppercase tracking-widest text-slate-500">Invalidation</h3>
            <ul class="mt-2 space-y-1">
              @for (iv of d.invalid_if; track iv) {
                <li class="flex gap-2 text-sm text-slate-300">
                  <span class="text-bear">✕</span><span>{{ iv }}</span>
                </li>
              }
            </ul>
          </div>

          <dl class="grid grid-cols-2 gap-x-4 gap-y-3 font-mono text-[0.78rem]">
            <div>
              <dt class="text-slate-500">Score</dt>
              <dd class="tabular text-slate-100">{{ d.score | number: '1.1-1' }}</dd>
            </div>
            <div>
              <dt class="text-slate-500">Agreement</dt>
              <dd class="text-slate-100">{{ d.agreement }}</dd>
            </div>
            <div>
              <dt class="text-slate-500">Automation</dt>
              <dd class="text-slate-100">{{ d.automation }}</dd>
            </div>
            <div>
              <dt class="text-slate-500">Weights</dt>
              <dd class="text-slate-100">{{ d.weights_version }}</dd>
            </div>
            <div class="col-span-2">
              <dt class="text-slate-500">As of</dt>
              <dd class="text-slate-300">{{ d.ts | date: 'medium' }}</dd>
            </div>
          </dl>
        </section>
      } @else {
        <div class="flex flex-1 flex-col items-center justify-center gap-3">
          <p class="font-mono text-sm text-slate-500">No actionable decision right now (fail-closed).</p>
          <a routerLink="/journal" class="text-gold-300 underline">Open journal</a>
        </div>
      }
    </main>
  `
})
export class ConfluenceComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly decision = signal<ConfluenceDecision | null>(null);
  readonly ict = signal<IctSnapshot | null>(null);
  readonly gann = signal<GannSnapshot | null>(null);
  readonly tradingStyle = signal<string>('DAY');
  readonly loading = signal(true);
  readonly acting = signal(false);
  readonly actionMsg = signal<string | null>(null);
  readonly actionErr = signal(false);
  readonly user = this.auth.user;

  readonly styleLabel = computed(() => this.tradingStyle() || 'DAY');

  readonly engineTags = computed(() => {
    const tags: string[] = [];
    const ict = this.ict();
    const d = this.decision();
    if (ict?.zones?.active_ote) tags.push('OTE');
    if ((ict?.zones?.breakers?.length ?? 0) > 0) tags.push('BREAKER');
    if ((ict?.zones?.ifvgs?.length ?? 0) > 0) tags.push('IFVG');
    if ((ict?.zones?.order_blocks?.length ?? 0) > 0) tags.push('OB');
    if ((ict?.zones?.fvgs?.length ?? 0) > 0) tags.push('FVG');
    if (d?.reasons?.some((r) => r.includes('SMT') || r.includes('DXY'))) tags.push('SMT');
    if (d?.reasons?.some((r) => r.includes('UNICORN'))) tags.push('UNICORN');
    if (d?.reasons?.some((r) => r.includes('EQH') || r.includes('EQL') || r.includes('ROUND'))) tags.push('LIQ');
    return tags;
  });

  readonly banner = computed(() => {
    if (this.api.usingMock()) {
      return 'Backend unreachable — showing mock confluence. Actions are simulated locally, not journaled on the server.';
    }
    const h = this.api.health();
    if (h && h.status !== 'ok') {
      return `API health: ${h.status} — data may be stale. Never trade a degraded feed.`;
    }
    if (this.auth.usingDevToken()) {
      return 'Signed in with DEV_TOKEN (demo). Not a real CSS session.';
    }
    return null;
  });

  readonly headlineReason = computed(() => {
    const d = this.decision();
    if (!d) return '';
    const chips = d.reasons.filter((r) => r !== 'ALIGN_LONG' && r !== 'ALIGN_SHORT' && r !== 'CONFLICT');
    const pretty = chips.slice(0, 3).map((r) => this.humanize(r));
    return pretty.length ? pretty.join(' + ') + '.' : 'Fail-closed: no confluence.';
  });

  ngOnInit(): void {
    this.api.getHealth().subscribe();
    this.api.getOpsStatus().subscribe({
      next: (s) => {
        if (s?.tradingStyle) this.tradingStyle.set(s.tradingStyle);
        else if (s?.trading_style) this.tradingStyle.set(s.trading_style);
      }
    });
    forkJoin({
      decision: this.api.getLatestDecision(),
      ict: this.api.getIctSnapshot(),
      gann: this.api.getGannSnapshot()
    }).subscribe({
      next: ({ decision, ict, gann }) => {
        this.decision.set(decision);
        this.ict.set(ict);
        this.gann.set(gann);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  canConfirm(d: ConfluenceDecision): boolean {
    return d.automation !== 'deny' && d.grade !== 'F' && d.mode !== 'NONE';
  }

  confirm(d: ConfluenceDecision): void {
    this.acting.set(true);
    this.api.confirm(d.id).subscribe({
      next: (row) => {
        this.acting.set(false);
        this.actionErr.set(false);
        this.actionMsg.set(`Paper position ${row.status} — logged to journal (${row.id}).`);
      },
      error: (e) => {
        this.acting.set(false);
        this.actionErr.set(true);
        this.actionMsg.set(e?.error?.message || 'Confirm rejected by server (risk/conflict?).');
      }
    });
  }

  dismiss(d: ConfluenceDecision): void {
    this.acting.set(true);
    this.api.dismiss(d.id).subscribe({
      next: (row) => {
        this.acting.set(false);
        this.actionErr.set(false);
        this.actionMsg.set(`Decision dismissed — journal row ${row.id}.`);
      },
      error: (e) => {
        this.acting.set(false);
        this.actionErr.set(true);
        this.actionMsg.set(e?.error?.message || 'Dismiss failed.');
      }
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }

  dirWord(d: Direction): string {
    return d === 'long' ? 'LONG' : d === 'short' ? 'SHORT' : 'FLAT';
  }
  modeWord(m: string): string {
    return { R: 'Mode R · Reversal', C: 'Mode C · Continuation', T: 'Mode T · Watch', NONE: 'No mode' }[m] ?? m;
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

  private humanize(code: string): string {
    return code
      .replace(/^ICT_/, '')
      .replace(/^GANN_/, '')
      .replace(/^KZ_/, 'killzone ')
      .replace(/_/g, ' ')
      .toLowerCase();
  }
}

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'tp-login',
  standalone: true,
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="flex min-h-screen items-center justify-center px-5 py-10">
      <section class="w-full max-w-sm">
        <header class="mb-9 text-center">
          <p class="font-mono text-[0.7rem] uppercase tracking-[0.42em] text-gold-400/80">XAUUSD · Paper</p>
          <h1 class="mt-3 font-display text-4xl font-600 leading-none text-slate-50">Trading Portal</h1>
          <p class="mt-3 text-sm text-slate-400">ICT + Gann confluence, one decision at a time.</p>
        </header>

        <form
          class="rounded-2xl border border-obsidian-600/70 bg-obsidian-800/70 p-6 shadow-glow backdrop-blur"
          (ngSubmit)="submit()"
        >
          <label class="block">
            <span class="font-mono text-[0.68rem] uppercase tracking-widest text-slate-400">Operator</span>
            <input
              name="username"
              [(ngModel)]="username"
              autocomplete="username"
              required
              class="mt-1.5 w-full rounded-lg border border-obsidian-600 bg-obsidian-900 px-3 py-2.5 text-slate-100 outline-none transition focus:border-gold-500 focus:ring-1 focus:ring-gold-500/40"
              placeholder="operator"
            />
          </label>

          <label class="mt-4 block">
            <span class="font-mono text-[0.68rem] uppercase tracking-widest text-slate-400">Password</span>
            <input
              name="password"
              type="password"
              [(ngModel)]="password"
              autocomplete="current-password"
              required
              class="mt-1.5 w-full rounded-lg border border-obsidian-600 bg-obsidian-900 px-3 py-2.5 text-slate-100 outline-none transition focus:border-gold-500 focus:ring-1 focus:ring-gold-500/40"
              placeholder="••••••••"
            />
          </label>

          @if (error()) {
            <p class="mt-4 rounded-lg border border-bear/40 bg-bear/10 px-3 py-2 text-sm text-bear">{{ error() }}</p>
          }

          <button
            type="submit"
            [disabled]="loading()"
            class="mt-6 flex w-full items-center justify-center gap-2 rounded-lg bg-gold-500 px-4 py-3 font-600 text-obsidian-950 transition hover:bg-gold-400 disabled:opacity-60"
          >
            {{ loading() ? 'Signing in…' : 'Sign in via CSS' }}
          </button>

          <p class="mt-4 text-center font-mono text-[0.66rem] leading-relaxed text-slate-500">
            CSS DEV IdP {{ cssHost }}<br />clientId=<span class="text-gold-400/90">{{ clientId }}</span>
          </p>

          @if (hasDevToken) {
            <button
              type="button"
              (click)="useDevToken()"
              class="mt-4 w-full rounded-lg border border-obsidian-600 px-4 py-2.5 text-sm text-slate-300 transition hover:border-gold-500/60 hover:text-gold-300"
            >
              Continue with DEV_TOKEN (demo)
            </button>
          }
        </form>
      </section>
    </main>
  `
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly cssHost = environment.cssUrl;
  readonly clientId = environment.clientId;
  readonly hasDevToken = !!environment.devToken;

  submit(): void {
    if (!this.username || !this.password) {
      this.error.set('Enter operator username and password.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigateByUrl('/');
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(this.describe(err));
      }
    });
  }

  useDevToken(): void {
    if (this.auth.useDevToken()) {
      this.router.navigateByUrl('/');
    }
  }

  private describe(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) {
        return `Cannot reach CSS at ${environment.cssUrl || location.origin}. Start the IdP or check nginx /auth.`;
      }
      const cssMsg =
        typeof err.error === 'string'
          ? err.error
          : err.error && typeof err.error === 'object' && 'message' in err.error
            ? String((err.error as { message?: string }).message || '')
            : '';
      if (err.status === 401 || err.status === 403) {
        if (/unknown or disabled client/i.test(cssMsg)) {
          return `CSS rejected clientId "${environment.clientId}" (not registered/enabled on this IdP). ${cssMsg}`;
        }
        if (/no roles for application/i.test(cssMsg)) {
          return `User has no roles on clientId "${environment.clientId}". Ask ops to grant ROLE_USER.`;
        }
        return cssMsg || `Login failed for clientId="${environment.clientId}" (check username/password).`;
      }
      return `CSS login failed (${err.status}). ${cssMsg}`.trim();
    }
    return err instanceof Error ? err.message : 'Login failed.';
  }
}

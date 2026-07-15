import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

const TOKEN_KEY = 'tp.jwt';
const USER_KEY = 'tp.user';

interface LoginResponse {
  // CSS DEV /auth/login legacy shape is tolerant: accept common token field names.
  token?: string;
  access_token?: string;
  accessToken?: string;
  jwt?: string;
  username?: string;
  sub?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _token = signal<string | null>(this.readToken());
  private readonly _user = signal<string | null>(localStorage.getItem(USER_KEY));

  readonly token = this._token.asReadonly();
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => !!this._token());
  /** True when running off the DEV demo token rather than a real CSS session. */
  readonly usingDevToken = computed(() => !!environment.devToken && this._token() === environment.devToken);

  constructor(private readonly http: HttpClient) {}

  private readToken(): string | null {
    return localStorage.getItem(TOKEN_KEY) || (environment.devToken || null);
  }

  /** Password login against CSS DEV IdP (:9000) — legacy pattern still supported. */
  login(username: string, password: string): Observable<LoginResponse> {
    const url = `${environment.cssUrl}/auth/login`;
    const body = { username, password, clientId: environment.clientId };
    return this.http.post<LoginResponse>(url, body).pipe(
      tap((res) => {
        const token = res.token || res.access_token || res.accessToken || res.jwt;
        if (!token) {
          throw new Error('CSS login succeeded but no token field was present in the response.');
        }
        this.setSession(token, res.username || res.sub || username);
      })
    );
  }

  /** Demo escape hatch: use DEV_TOKEN from environment without hitting CSS. */
  useDevToken(): boolean {
    if (!environment.devToken) {
      return false;
    }
    this.setSession(environment.devToken, 'dev-token');
    return true;
  }

  setSession(token: string, user: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, user);
    this._token.set(token);
    this._user.set(user);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._token.set(null);
    this._user.set(null);
  }
}

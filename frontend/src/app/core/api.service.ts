import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AnalyticsBySessionResponse,
  AnalyticsSummary,
  ConfluenceDecision,
  GannSnapshot,
  HealthResponse,
  IctSnapshot,
  JournalListResponse,
  OhlcBar,
  PaperJournalEntry,
  StyleInfo,
  Timeframe,
  TradingStyle
} from './models';
import { MOCK_DECISION, MOCK_GANN, MOCK_ICT, mockJournal, mockOhlc } from './mock-data';

const TF_MS: Record<Timeframe, number> = {
  M1: 60_000,
  M5: 5 * 60_000,
  M15: 15 * 60_000,
  H1: 3_600_000,
  H4: 4 * 3_600_000,
  D1: 24 * 3_600_000
};

// Deterministic offline fixtures for the analytics page (kept local — mock-data.ts owned elsewhere).
const MOCK_ANALYTICS_SUMMARY: AnalyticsSummary = {
  trade_count: 42,
  win_rate: 0.55,
  expectancy_r: 0.31,
  profit_factor: 1.62,
  avg_win_r: 1.48,
  avg_loss_r: -0.86,
  total_r: 13.0,
  as_of: new Date().toISOString()
};

const MOCK_ANALYTICS_BY_SESSION: AnalyticsBySessionResponse = {
  sessions: [
    { session: 'ASIA', trade_count: 9, win_rate: 0.44, expectancy_r: 0.05 },
    { session: 'LONDON', trade_count: 15, win_rate: 0.6, expectancy_r: 0.42 },
    { session: 'NY', trade_count: 18, win_rate: 0.56, expectancy_r: 0.38 }
  ]
};

/**
 * Talks to the Spring Boot API (:3340). The backend may not be up yet — every
 * read fails gracefully to deterministic mock data and flips `usingMock` so the
 * UI can show a banner (per hire rule: "mock gracefully if API down").
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  readonly usingMock = signal(false);
  readonly health = signal<HealthResponse | null>(null);

  constructor(private readonly http: HttpClient) {}

  private base(path: string): string {
    return `${environment.apiUrl}${path}`;
  }

  getHealth(): Observable<HealthResponse | null> {
    return this.http.get<HealthResponse>(this.base('/api/health')).pipe(
      tap((h) => this.health.set(h)),
      catchError(() => {
        this.health.set({ status: 'down', ts: new Date().toISOString() });
        return of(null);
      })
    );
  }

  getLatestDecision(): Observable<ConfluenceDecision> {
    return this.http.get<ConfluenceDecision>(this.base('/api/confluence/decision')).pipe(
      tap(() => this.usingMock.set(false)),
      catchError(() => {
        this.usingMock.set(true);
        return of(MOCK_DECISION);
      })
    );
  }

  getIctSnapshot(): Observable<IctSnapshot | null> {
    return this.http.get<IctSnapshot>(this.base('/api/engines/ict/snapshot')).pipe(
      tap(() => this.usingMock.set(false)),
      catchError(() => {
        this.usingMock.set(true);
        return of(MOCK_ICT);
      })
    );
  }

  getGannSnapshot(): Observable<GannSnapshot | null> {
    return this.http.get<GannSnapshot>(this.base('/api/engines/gann/snapshot')).pipe(
      tap(() => this.usingMock.set(false)),
      catchError(() => {
        this.usingMock.set(true);
        return of(MOCK_GANN);
      })
    );
  }

  listJournal(params: { grade?: string; direction?: string; status?: string; limit?: number } = {}): Observable<JournalListResponse> {
    let hp = new HttpParams();
    if (params.grade) hp = hp.set('grade', params.grade);
    if (params.direction) hp = hp.set('direction', params.direction);
    if (params.status) hp = hp.set('status', params.status);
    hp = hp.set('limit', String(params.limit ?? 50));
    return this.http.get<JournalListResponse>(this.base('/api/paper/journal'), { params: hp }).pipe(
      tap(() => this.usingMock.set(false)),
      catchError(() => {
        this.usingMock.set(true);
        const items = mockJournal();
        return of({ items, total: items.length, limit: params.limit ?? 50, offset: 0 });
      })
    );
  }

  /** Ops fleet status (style + feature inventory). Soft-fail if unauthorized/unreachable. */
  getOpsStatus(): Observable<{ tradingStyle?: string; trading_style?: string; features?: Record<string, string> } | null> {
    return this.http.get<Record<string, unknown>>(this.base('/api/ops/status')).pipe(
      map((raw) => ({
        tradingStyle: (raw['tradingStyle'] ?? raw['trading_style']) as string | undefined,
        trading_style: (raw['trading_style'] ?? raw['tradingStyle']) as string | undefined,
        features: (raw['features'] as Record<string, string>) ?? undefined
      })),
      catchError(() => of(null))
    );
  }

  confirm(decisionId: string, note?: string): Observable<PaperJournalEntry> {
    return this.http
      .post<PaperJournalEntry>(this.base('/api/paper/confirm'), { decision_id: decisionId, note })
      .pipe(
        tap(() => this.usingMock.set(false)),
        catchError(() => {
          this.usingMock.set(true);
          return of(this.simulate(decisionId, 'PAPER_OPEN', note));
        })
      );
  }

  dismiss(decisionId: string, reason?: string): Observable<PaperJournalEntry> {
    return this.http
      .post<PaperJournalEntry>(this.base('/api/paper/dismiss'), { decision_id: decisionId, reason })
      .pipe(
        tap(() => this.usingMock.set(false)),
        catchError(() => {
          this.usingMock.set(true);
          return of(this.simulate(decisionId, 'DISMISSED', reason));
        })
      );
  }

  /** OHLC bars for the candle chart. `bars` counts back from now at the timeframe's native spacing. */
  getOhlc(tf: Timeframe = 'M15', bars = 96): Observable<OhlcBar[]> {
    const to = new Date();
    const from = new Date(to.getTime() - bars * TF_MS[tf]);
    const params = new HttpParams().set('tf', tf).set('from', from.toISOString()).set('to', to.toISOString());
    return this.http.get<OhlcBar[]>(this.base('/api/market/xauusd/ohlc'), { params }).pipe(
      tap(() => this.usingMock.set(false)),
      catchError(() => {
        this.usingMock.set(true);
        return of(mockOhlc(tf, bars));
      })
    );
  }

  /** User-selected trading style. Sibling backend endpoint may not exist yet — soft-fail to null so callers can fall back to ops tradingStyle. */
  getStyle(): Observable<StyleInfo | null> {
    return this.http.get<StyleInfo>(this.base('/api/style')).pipe(catchError(() => of(null)));
  }

  /** Persist the user-selected trading style. Soft-fails to null on error (e.g. endpoint not deployed yet). */
  putStyle(style: TradingStyle): Observable<StyleInfo | null> {
    return this.http
      .put<StyleInfo>(this.base('/api/style'), { style })
      .pipe(catchError(() => of(null)));
  }

  /** Headline paper-trading performance. Soft-fails to a zeroed summary (renders empty state). */
  getAnalyticsSummary(): Observable<AnalyticsSummary> {
    return this.http.get<AnalyticsSummary>(this.base('/api/analytics/summary')).pipe(
      tap(() => this.usingMock.set(false)),
      catchError(() => {
        this.usingMock.set(true);
        return of(MOCK_ANALYTICS_SUMMARY);
      })
    );
  }

  /** Win rate / expectancy grouped by session (Asia/London/NY). Soft-fails to empty list. */
  getAnalyticsBySession(): Observable<AnalyticsBySessionResponse> {
    return this.http.get<AnalyticsBySessionResponse>(this.base('/api/analytics/by-session')).pipe(
      tap(() => this.usingMock.set(false)),
      catchError(() => {
        this.usingMock.set(true);
        return of(MOCK_ANALYTICS_BY_SESSION);
      })
    );
  }

  /** Runs the existing paper-only backtest and returns its embedded trades_csv for client-side export. */
  exportBacktestCsv(): Observable<string | null> {
    return this.http.post<{ trades_csv?: string }>(this.base('/api/backtest/run'), {}).pipe(
      map((res) => res?.trades_csv ?? null),
      catchError(() => of(null))
    );
  }

  /** Local optimistic row when the paper API is unreachable (demo/offline). */
  private simulate(decisionId: string, status: 'PAPER_OPEN' | 'DISMISSED', note?: string): PaperJournalEntry {
    const d = MOCK_DECISION;
    return {
      id: `local-${Date.now()}`,
      decision_id: decisionId,
      symbol: 'XAUUSD',
      session_date: new Date().toISOString().slice(0, 10),
      status,
      mode: d.mode,
      direction: d.direction,
      grade: d.grade,
      score: d.score,
      reasons: d.reasons,
      weights_version: d.weights_version,
      entry: d.entry,
      stop: d.stop,
      targets: d.targets,
      invalid_if: d.invalid_if,
      automation: d.automation,
      detected_at: d.ts,
      actioned_at: new Date().toISOString(),
      actioned_by: 'local-demo',
      action_note: note ?? null
    };
  }
}

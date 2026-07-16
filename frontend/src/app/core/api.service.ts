import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ConfluenceDecision,
  GannSnapshot,
  HealthResponse,
  IctSnapshot,
  JournalListResponse,
  PaperJournalEntry
} from './models';
import { MOCK_DECISION, MOCK_GANN, MOCK_ICT, mockJournal } from './mock-data';

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

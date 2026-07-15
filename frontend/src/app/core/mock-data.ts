import { ConfluenceDecision, PaperJournalEntry } from './models';

// Deterministic offline fixtures so the portal is demoable with the backend down.
// Numbers are illustrative XAUUSD levels, not a live signal.
export const MOCK_DECISION: ConfluenceDecision = {
  id: 'mock-decision-0001',
  symbol: 'XAUUSD',
  ts: new Date().toISOString(),
  mode: 'R',
  direction: 'short',
  grade: 'A+',
  score: 8.4,
  agreement: 'ALIGN_SHORT',
  reasons: ['ICT_SWEEP_ASIA_HIGH', 'GANN_SO9_RESISTANCE', 'KZ_NY_OPEN', 'GANN_STRETCH_HIGH', 'ALIGN_SHORT'],
  entry: { type: 'OB+FVG', low: 2412.4, high: 2414.8 },
  stop: 2418.9,
  targets: [2404.5, 2396.0],
  invalid_if: ['H1 close above 2419.0', 'NY open sweep of PDH invalidates fade'],
  engines: { ict_ref: 'ict@2026-07-15T13:31:00Z', gann_ref: 'gann@2026-07-15T13:31:00Z' },
  automation: 'confirm',
  weights_version: 'cw-2026.07.0'
};

export function mockJournal(): PaperJournalEntry[] {
  const day = new Date().toISOString().slice(0, 10);
  return [
    {
      id: 'mock-j-3',
      decision_id: 'mock-decision-0001',
      symbol: 'XAUUSD',
      session_date: day,
      status: 'PAPER_OPEN',
      mode: 'R',
      direction: 'short',
      grade: 'A+',
      score: 8.4,
      reasons: ['ICT_SWEEP_ASIA_HIGH', 'GANN_SO9_RESISTANCE', 'KZ_NY_OPEN'],
      weights_version: 'cw-2026.07.0',
      entry: { type: 'OB+FVG', low: 2412.4, high: 2414.8 },
      stop: 2418.9,
      targets: [2404.5, 2396.0],
      invalid_if: ['H1 close above 2419.0'],
      automation: 'confirm',
      risk: { ok: true, size: 0.5, deny_reasons: [] },
      detected_at: new Date(Date.now() - 40 * 60_000).toISOString(),
      actioned_at: new Date(Date.now() - 38 * 60_000).toISOString(),
      actioned_by: 'operator',
      paper: { opened_at: new Date(Date.now() - 38 * 60_000).toISOString(), entry_price: 2413.6 }
    },
    {
      id: 'mock-j-2',
      decision_id: 'mock-decision-0000',
      symbol: 'XAUUSD',
      session_date: day,
      status: 'DISMISSED',
      mode: 'C',
      direction: 'long',
      grade: 'B',
      score: 5.1,
      reasons: ['ICT_BOS_M15', 'KZ_LONDON', 'GANN_1X1_SUPPORT'],
      weights_version: 'cw-2026.07.0',
      entry: { type: 'FVG', low: 2401.0, high: 2402.6 },
      stop: 2397.2,
      targets: [2409.0],
      invalid_if: ['M15 close below 2397.0'],
      automation: 'confirm',
      risk: { ok: true, size: 0.5, deny_reasons: [] },
      detected_at: new Date(Date.now() - 3 * 3600_000).toISOString(),
      actioned_at: new Date(Date.now() - 2.9 * 3600_000).toISOString(),
      actioned_by: 'operator',
      action_note: 'Chop into London fix — skipped.'
    },
    {
      id: 'mock-j-1',
      decision_id: 'mock-decision-neg1',
      symbol: 'XAUUSD',
      session_date: day,
      status: 'REJECTED',
      mode: 'NONE',
      direction: 'flat',
      grade: 'F',
      score: 1.2,
      reasons: ['CONFLICT', 'NEWS_VETO_CPI'],
      weights_version: 'cw-2026.07.0',
      entry: { type: 'none', low: 0, high: 0 },
      stop: 0,
      targets: [],
      invalid_if: ['n/a'],
      automation: 'deny',
      risk: { ok: false, size: null, deny_reasons: ['CONFLICT', 'NEWS_WINDOW'] },
      detected_at: new Date(Date.now() - 5 * 3600_000).toISOString(),
      actioned_at: null,
      actioned_by: null
    }
  ];
}

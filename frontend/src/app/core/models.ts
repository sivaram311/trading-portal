// Types mirror docs/contracts/openapi.yaml + docs/contracts/schemas/*.json.
// Kept intentionally close to the contract field names for 1:1 auditability.

export type Grade = 'A+' | 'A' | 'B' | 'C' | 'F';
export type Mode = 'R' | 'C' | 'T' | 'NONE';
export type Direction = 'long' | 'short' | 'flat';
export type Agreement = 'ALIGN_LONG' | 'ALIGN_SHORT' | 'CONFLICT' | 'SOFT' | 'NONE';
export type Automation = 'allow' | 'confirm' | 'deny';

export type JournalStatus =
  | 'DETECTED'
  | 'SCORED'
  | 'RISK_CHECKED'
  | 'REJECTED'
  | 'ALERTED'
  | 'DISMISSED'
  | 'PAPER_OPEN'
  | 'PAPER_CLOSED';

export interface EntryZone {
  type: string;
  low: number;
  high: number;
}

export interface ConfluenceDecision {
  id: string;
  symbol: 'XAUUSD';
  ts: string;
  mode: Mode;
  direction: Direction;
  grade: Grade;
  score: number;
  agreement: Agreement;
  reasons: string[];
  entry: EntryZone;
  stop: number;
  targets: number[];
  invalid_if: string[];
  engines: { ict_ref: string; gann_ref: string };
  automation: Automation;
  weights_version: string;
}

export interface RiskEmbed {
  ok: boolean;
  size: number | null;
  deny_reasons: string[];
}

export interface PaperJournalEntry {
  id: string;
  decision_id: string;
  symbol: 'XAUUSD';
  session_date: string;
  status: JournalStatus;
  mode: Mode;
  direction: Direction;
  grade: Grade;
  score: number;
  reasons: string[];
  weights_version: string;
  entry: EntryZone;
  stop: number;
  targets: number[];
  invalid_if: string[];
  automation: Automation;
  risk?: RiskEmbed | null;
  detected_at: string;
  actioned_at?: string | null;
  actioned_by?: string | null;
  action_note?: string | null;
  paper?: {
    opened_at?: string | null;
    closed_at?: string | null;
    entry_price?: number | null;
    exit_price?: number | null;
    exit_reason?: 'STOP' | 'T1' | 'T2' | 'MANUAL' | 'EOD' | null;
    r_multiple?: number | null;
    mfe_r?: number | null;
    mae_r?: number | null;
  } | null;
}

export interface JournalListResponse {
  items: PaperJournalEntry[];
  total: number;
  limit: number;
  offset: number;
}

/** User-selectable trading cadence (distinct from the confluence engine `Mode`). */
export type TradingStyle = 'SCALP' | 'DAY' | 'POSITIONAL';

export interface StyleInfo {
  style: TradingStyle;
  source?: 'user' | 'ops' | 'default';
  updated_at?: string;
}

export interface HealthResponse {
  status: 'ok' | 'degraded' | 'down';
  ts: string;
  ny_time?: string;
  checks?: { db?: boolean; ingest?: boolean; mt5?: boolean };
}

/** ICT zone row (OB / FVG / BREAKER / IFVG when engine emits them). */
export interface IctZone {
  type: 'OB' | 'FVG' | 'BREAKER' | 'IFVG' | string;
  direction: 'bull' | 'bear' | string;
  low: number;
  high: number;
  state?: 'fresh' | 'tested' | 'filled' | string;
  ts?: string;
}

export interface IctOteZone {
  deep: number;
  sweet: number;
  shallow: number;
  invalidation: number;
}

/** Minimal ICT snapshot fields used by the price rail overlay. */
export interface IctSnapshot {
  symbol: 'XAUUSD';
  asof: string;
  zones: {
    order_blocks: IctZone[];
    fvgs: IctZone[];
    breakers?: IctZone[];
    ifvgs?: IctZone[];
    active_entry?: IctZone | null;
    active_ote?: IctOteZone | null;
  };
}

export interface GannSo9Level {
  kind: 'fine' | 'odd' | 'even' | string;
  k: number;
  price: number;
  dist: number;
}

/** Minimal Gann snapshot fields used by the price rail overlay. */
export interface GannSnapshot {
  symbol: 'XAUUSD';
  asof: string;
  angle: {
    equilibrium: number;
  };
  so9: {
    levels: GannSo9Level[];
    at_level: boolean;
    nearest?: GannSo9Level | null;
  };
}

export type Timeframe = 'M1' | 'M5' | 'M15' | 'H1' | 'H4' | 'D1';

/** One OHLCV bar for XAUUSD (docs/contracts/schemas/ohlc-bar.json). */
export interface OhlcBar {
  symbol: 'XAUUSD';
  tf: Timeframe;
  ts: string;
  ny_time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  broker_time?: string | null;
}

/** GET /api/analytics/summary — headline paper-trading performance. */
export interface AnalyticsSummary {
  trade_count: number;
  win_rate: number;
  expectancy_r: number;
  profit_factor?: number | null;
  avg_win_r?: number | null;
  avg_loss_r?: number | null;
  total_r?: number | null;
  as_of?: string;
}

/** One row of GET /api/analytics/by-session. */
export interface SessionBreakdownRow {
  session: string;
  trade_count: number;
  win_rate: number;
  expectancy_r: number;
}

export interface AnalyticsBySessionResponse {
  sessions: SessionBreakdownRow[];
}

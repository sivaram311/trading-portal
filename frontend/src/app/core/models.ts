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

export interface HealthResponse {
  status: 'ok' | 'degraded' | 'down';
  ts: string;
  ny_time?: string;
  checks?: { db?: boolean; ingest?: boolean; mt5?: boolean };
}

-- V2: indexes for P5 LIVE_* journal rows + paper lifecycle queries.
-- LIVE_OPEN / LIVE_PARTIAL reuse paper_journal (no separate live table).
-- Status column already varchar(16); LIVE_PARTIAL fits.

create index if not exists ix_journal_live_open
    on paper_journal (status)
    where status in ('LIVE_OPEN', 'LIVE_PARTIAL');

create index if not exists ix_journal_decision_id
    on paper_journal (decision_id);

comment on table paper_journal is
    'Paper + micro-live journal. Statuses include PAPER_*, LIVE_OPEN, LIVE_PARTIAL, REJECTED, DISMISSED, ALERTED.';

-- Trading Portal DEV schema (schema `dev`, owned by app_trading_portal_dev).
-- Aligns to docs/contracts/schemas/*.json. Paper-only; no live execution tables.

create table if not exists ohlc_candle (
    id          bigserial primary key,
    symbol      varchar(16)      not null,
    tf          varchar(4)       not null,
    ts          timestamptz      not null,
    ny_time     timestamptz      not null,
    open        double precision not null,
    high        double precision not null,
    low         double precision not null,
    close       double precision not null,
    volume      double precision not null,
    broker_time timestamptz,
    constraint uq_ohlc_symbol_tf_ts unique (symbol, tf, ts)
);
create index if not exists ix_ohlc_symbol_tf_ts on ohlc_candle (symbol, tf, ts);

create table if not exists ict_snapshot (
    id         uuid             primary key,
    symbol     varchar(16)      not null,
    asof       timestamptz      not null,
    killzone   varchar(24),
    quality    integer          not null,
    payload    jsonb            not null,
    created_at timestamptz      not null default now()
);
create index if not exists ix_ict_asof on ict_snapshot (asof desc);

create table if not exists gann_snapshot (
    id         uuid             primary key,
    symbol     varchar(16)      not null,
    asof       timestamptz      not null,
    killzone   varchar(24),
    gann_bias  varchar(24),
    quality    integer          not null,
    payload    jsonb            not null,
    created_at timestamptz      not null default now()
);
create index if not exists ix_gann_asof on gann_snapshot (asof desc);

create table if not exists confluence_decision (
    id              uuid             primary key,
    symbol          varchar(16)      not null,
    ts              timestamptz      not null,
    mode            varchar(8)       not null,
    direction       varchar(8)       not null,
    grade           varchar(4)       not null,
    score           double precision not null,
    agreement       varchar(16)      not null,
    automation      varchar(8)       not null,
    weights_version varchar(32)      not null,
    ict_ref         varchar(64),
    gann_ref        varchar(64),
    payload         jsonb            not null,
    created_at      timestamptz      not null default now()
);
create index if not exists ix_decision_ts on confluence_decision (ts desc);

create table if not exists risk_verdict (
    id                 uuid             primary key,
    decision_id        uuid             not null,
    ts                 timestamptz      not null,
    ok                 boolean          not null,
    size               double precision,
    risk_per_trade_pct double precision,
    daily_loss_r       double precision not null default 0,
    open_positions     integer          not null default 0,
    deny_reasons       jsonb            not null,
    checks             jsonb            not null,
    payload            jsonb            not null,
    created_at         timestamptz      not null default now()
);
create index if not exists ix_verdict_decision on risk_verdict (decision_id);

create table if not exists paper_journal (
    id              uuid             primary key,
    decision_id     uuid             not null,
    symbol          varchar(16)      not null,
    session_date    date             not null,
    status          varchar(16)      not null,
    mode            varchar(8)       not null,
    direction       varchar(8)       not null,
    grade           varchar(4)       not null,
    score           double precision not null,
    weights_version varchar(32)      not null,
    automation      varchar(8)       not null,
    detected_at     timestamptz      not null,
    actioned_at     timestamptz,
    actioned_by     varchar(128),
    action_note     text,
    payload         jsonb            not null,
    created_at      timestamptz      not null default now()
);
create index if not exists ix_journal_session on paper_journal (session_date);
create index if not exists ix_journal_status on paper_journal (status);

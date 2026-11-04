-- 只增不改的历史存档，供日后做时间对齐的舆情回测：
-- 回放 t 日只能使用 fetched_at <= t 日收盘的新闻与 computed_at <= t 日收盘的舆情输入。
CREATE TABLE news_archive (
    id           BIGSERIAL PRIMARY KEY,
    stock_code   VARCHAR(20)  NOT NULL,
    title        TEXT         NOT NULL,
    published_at TIMESTAMP,
    url          TEXT,
    source       VARCHAR(40)  NOT NULL,
    fetched_at   TIMESTAMPTZ  NOT NULL,
    dedupe_key   TEXT         NOT NULL,
    CONSTRAINT uq_news_archive UNIQUE (stock_code, dedupe_key)
);
CREATE INDEX idx_news_archive_stock_fetched ON news_archive (stock_code, fetched_at);

CREATE TABLE sentiment_archive (
    id                 BIGSERIAL PRIMARY KEY,
    stock_code         VARCHAR(20)      NOT NULL,
    computed_at        TIMESTAMPTZ      NOT NULL,
    method             VARCHAR(40)      NOT NULL,
    sentiment_score    DOUBLE PRECISION,
    signal_coverage    DOUBLE PRECISION,
    news_signal        DOUBLE PRECISION,
    news_data_source   VARCHAR(40),
    news_stale         BOOLEAN,
    headline_count     INTEGER          NOT NULL DEFAULT 0,
    change_percent     DOUBLE PRECISION,
    pe                 DOUBLE PRECISION,
    turnover_rate      DOUBLE PRECISION,
    valuation_status   VARCHAR(20),
    market_update_time VARCHAR(40),
    score_explanation  TEXT
);
CREATE INDEX idx_sentiment_archive_stock_computed ON sentiment_archive (stock_code, computed_at);

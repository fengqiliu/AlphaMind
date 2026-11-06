-- 用户手动维护的当前组合（单用户、单行）。只用于推算个人模型 Gate 5 的交易后权重，不对接券商、不记交易流水。
CREATE TABLE portfolio_profile (
    id              SMALLINT         PRIMARY KEY CHECK (id = 1),
    holdings        JSONB            NOT NULL,
    cash            DOUBLE PRECISION NOT NULL,
    low_volatility  DOUBLE PRECISION NOT NULL,
    peak_value      DOUBLE PRECISION,
    updated_at      TIMESTAMPTZ      NOT NULL
);

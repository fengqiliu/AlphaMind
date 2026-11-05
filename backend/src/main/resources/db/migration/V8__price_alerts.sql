-- 个人模型价格提醒：价格首次进入更深的档位时记一条，只提醒去复核，不构成买入建议。
-- price_alert_state 记录每个标的已提醒过的最深档位，价格回到首档之上后重置，避免在档边反复提醒。
CREATE TABLE price_alert_state (
    stock_code       VARCHAR(20)      PRIMARY KEY,
    last_zone        VARCHAR(20)      NOT NULL,
    last_price       DOUBLE PRECISION NOT NULL,
    deepest_alerted  INTEGER          NOT NULL,
    checked_at       TIMESTAMPTZ      NOT NULL
);

CREATE TABLE price_alerts (
    id               BIGSERIAL        PRIMARY KEY,
    stock_code       VARCHAR(20)      NOT NULL,
    stock_name       VARCHAR(50)      NOT NULL,
    from_zone        VARCHAR(20)      NOT NULL,
    to_zone          VARCHAR(20)      NOT NULL,
    price            DOUBLE PRECISION NOT NULL,
    quoted_at        TIMESTAMPTZ,
    model_version    VARCHAR(20)      NOT NULL,
    triggered_at     TIMESTAMPTZ      NOT NULL,
    acknowledged_at  TIMESTAMPTZ
);
CREATE INDEX idx_price_alerts_unread ON price_alerts (triggered_at DESC) WHERE acknowledged_at IS NULL;

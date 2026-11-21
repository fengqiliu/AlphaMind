-- 持仓体检每日自动运行：某一检查项（种类 + 标的或主题）首次越线时记一条提醒，只提醒去复核，不给卖出价。
-- portfolio_health_state 记录当前处于越线状态的检查项：持续越线不重复提醒，消失后清除，再出现时重新提醒。
-- 缺当期行情导致无法检查的项保持原状态，不当作“已恢复”。
CREATE TABLE portfolio_health_state (
    finding_key   VARCHAR(120) PRIMARY KEY,
    kind          VARCHAR(30)  NOT NULL,
    active_since  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE portfolio_health_alerts (
    id               BIGSERIAL        PRIMARY KEY,
    kind             VARCHAR(30)      NOT NULL,
    stock_code       VARCHAR(20),
    name             VARCHAR(50)      NOT NULL,
    message          TEXT             NOT NULL,
    value            DOUBLE PRECISION,
    limit_value      DOUBLE PRECISION,
    triggered_at     TIMESTAMPTZ      NOT NULL,
    acknowledged_at  TIMESTAMPTZ
);
CREATE INDEX idx_portfolio_health_alerts_unread ON portfolio_health_alerts (triggered_at DESC) WHERE acknowledged_at IS NULL;

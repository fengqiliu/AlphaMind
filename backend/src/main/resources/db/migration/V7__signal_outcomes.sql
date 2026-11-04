-- 信号账本：每份报告 × 每个持有期一行，到期后写入、不再修改。
-- 报告属性（信号、一致度、策略、模式、个人模型等级）在写入时冻结，统计不依赖报告 JSON 的后续变化。
CREATE TABLE signal_outcomes (
    id                BIGSERIAL PRIMARY KEY,
    report_id         VARCHAR(36)      NOT NULL REFERENCES analysis_reports (id) ON DELETE CASCADE,
    stock_code        VARCHAR(20)      NOT NULL,
    horizon_days      INTEGER          NOT NULL,
    signal_type       VARCHAR(10),
    confidence_value  DOUBLE PRECISION,
    strategy          VARCHAR(20),
    analysis_mode     VARCHAR(20),
    personal_level    VARCHAR(10),
    signal_date       DATE             NOT NULL,
    entry_date        DATE             NOT NULL,
    entry_price       DOUBLE PRECISION NOT NULL,
    exit_date         DATE             NOT NULL,
    exit_price        DOUBLE PRECISION NOT NULL,
    gross_return      DOUBLE PRECISION NOT NULL,
    net_return        DOUBLE PRECISION NOT NULL,
    benchmark_return  DOUBLE PRECISION,
    excess_return     DOUBLE PRECISION,
    entry_blocked     BOOLEAN          NOT NULL,
    evaluated_at      TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uq_signal_outcome UNIQUE (report_id, horizon_days)
);
CREATE INDEX idx_signal_outcomes_report ON signal_outcomes (report_id);

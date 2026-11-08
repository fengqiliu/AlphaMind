-- 组合每日快照：收盘后记录一次按当期行情估值的组合总资产。
-- 缺任何一只市场定价持仓的当期行情时当天不写，不用过期缓存补数；同一天重复运行只覆盖当天一行。
CREATE TABLE portfolio_snapshots (
    snapshot_date   DATE             PRIMARY KEY,
    total_value     DOUBLE PRECISION NOT NULL,
    cash_ratio      DOUBLE PRECISION NOT NULL,
    holdings_count  INTEGER          NOT NULL,
    recorded_at     TIMESTAMPTZ      NOT NULL
);

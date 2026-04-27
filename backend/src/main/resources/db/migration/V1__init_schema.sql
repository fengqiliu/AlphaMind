-- AlphaMind 数据库初始化脚本
-- 数据库: PostgreSQL 14+
-- 使用 jsonb 存储复杂分析结果，方便查询和扩展

-- ==========================================
-- 1. 分析报告表
-- ==========================================
CREATE TABLE IF NOT EXISTS analysis_reports (
    id              VARCHAR(36)  PRIMARY KEY,
    stock_code      VARCHAR(20)  NOT NULL,
    stock_name      VARCHAR(100) NOT NULL,
    strategy        VARCHAR(20)  NOT NULL DEFAULT 'BALANCED',
    enable_debate   BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 交易信号
    signal_type     VARCHAR(10),                    -- BUY / SELL / HOLD
    entry_price     NUMERIC(12, 4),
    target_price    NUMERIC(12, 4),
    stop_loss       NUMERIC(12, 4),
    holding_days    INTEGER,
    rationale       TEXT,
    -- 置信度
    confidence_value   NUMERIC(5, 4),
    confidence_level   VARCHAR(10),                 -- HIGH / MEDIUM / LOW
    -- 完整分析数据（jsonb 支持灵活查询）
    market_data          JSONB,
    technical_indicators JSONB,
    sentiment_data       JSONB,
    judgment             JSONB,
    -- 元数据
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_stock_code ON analysis_reports (stock_code);
CREATE INDEX idx_analysis_created_at ON analysis_reports (created_at DESC);
CREATE INDEX idx_analysis_signal_type ON analysis_reports (signal_type);
-- jsonb 索引，支持按市场数据内字段查询
CREATE INDEX idx_analysis_market_data ON analysis_reports USING GIN (market_data);

-- ==========================================
-- 2. 用户自选股表
-- ==========================================
CREATE TABLE IF NOT EXISTS watchlist_items (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         VARCHAR(100) NOT NULL DEFAULT 'default',
    stock_code      VARCHAR(20)  NOT NULL,
    stock_name      VARCHAR(100) NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, stock_code)
);

CREATE INDEX idx_watchlist_user_id ON watchlist_items (user_id);
CREATE INDEX idx_watchlist_stock_code ON watchlist_items (stock_code);

-- ==========================================
-- 3. 聊天会话表
-- ==========================================
CREATE TABLE IF NOT EXISTS chat_sessions (
    session_id      VARCHAR(36)  PRIMARY KEY,
    user_id         VARCHAR(100) NOT NULL DEFAULT 'default',
    stock_code      VARCHAR(20),
    stock_name      VARCHAR(100),
    strategy        VARCHAR(20),
    message_count   INTEGER      NOT NULL DEFAULT 0,
    last_active_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_sessions_user ON chat_sessions (user_id, last_active_at DESC);
CREATE INDEX idx_chat_sessions_stock ON chat_sessions (stock_code);

-- ==========================================
-- 4. 聊天消息表
-- ==========================================
CREATE TABLE IF NOT EXISTS chat_messages (
    id              VARCHAR(36)  PRIMARY KEY,
    session_id      VARCHAR(36)  NOT NULL REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    role            VARCHAR(20)  NOT NULL,           -- user / assistant / system
    content         TEXT         NOT NULL,
    agent_type      VARCHAR(20),                     -- MARKET / TECHNICAL / SENTIMENT / PORTFOLIO 等
    agent_name      VARCHAR(50),
    model_used      VARCHAR(50),
    token_count     INTEGER,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_messages_session ON chat_messages (session_id, created_at ASC);
CREATE INDEX idx_chat_messages_agent ON chat_messages (agent_type);

-- ==========================================
-- 5. 股票基础信息缓存表
-- ==========================================
CREATE TABLE IF NOT EXISTS stock_info (
    stock_code      VARCHAR(20)  PRIMARY KEY,
    stock_name      VARCHAR(100) NOT NULL,
    market          VARCHAR(20),                     -- 上海主板 / 深圳主板 / 创业板 / 科创板
    industry        VARCHAR(50),
    -- 最新行情快照（每次分析后更新）
    current_price   NUMERIC(12, 4),
    change_percent  NUMERIC(8, 4),
    market_cap      BIGINT,
    pe_ratio        NUMERIC(10, 4),
    -- 元数据
    last_updated    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_info_industry ON stock_info (industry);
CREATE INDEX idx_stock_info_market   ON stock_info (market);

-- ==========================================
-- 触发器：自动更新 updated_at
-- ==========================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_analysis_reports_updated
    BEFORE UPDATE ON analysis_reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_watchlist_items_updated
    BEFORE UPDATE ON watchlist_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

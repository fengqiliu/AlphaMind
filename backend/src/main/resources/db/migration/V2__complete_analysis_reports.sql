-- 旧版 strategy / enable_debate 由默认值填充，无法可靠还原实际执行配置。
-- 保留原始列值，analysis_mode 为 NULL 的旧报告由读取层标记为配置未记录。
ALTER TABLE analysis_reports
    ADD COLUMN analysis_mode VARCHAR(20),
    ADD COLUMN final_signal_type VARCHAR(10),
    ADD COLUMN confidence_explanation TEXT,
    ADD COLUMN debate_views JSONB;

ALTER TABLE analysis_reports
    ADD CONSTRAINT chk_analysis_mode CHECK (analysis_mode IN ('PIPELINE', 'DEBATE')),
    ADD CONSTRAINT chk_final_signal_type CHECK (final_signal_type IN ('BUY', 'SELL', 'HOLD')),
    ADD CONSTRAINT chk_debate_views_array CHECK (jsonb_typeof(debate_views) = 'array');

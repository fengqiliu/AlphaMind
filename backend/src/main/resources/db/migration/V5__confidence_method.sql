-- 旧值保持NULL，不用新算法解释旧报告。
ALTER TABLE analysis_reports ADD COLUMN confidence_method VARCHAR(40);

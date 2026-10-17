-- 只索引有来源的个人复核，空复核和通用分析不会遮住上一份可复用证据。
CREATE INDEX idx_analysis_personal_review_latest
    ON analysis_reports (stock_code, created_at DESC, id DESC)
    WHERE personal_model IS NOT NULL
      AND (personal_model #>> '{evidence,evidenceSource}') ~ '[^[:space:]　]';

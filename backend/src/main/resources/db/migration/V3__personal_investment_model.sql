-- 与每次个人模型复核共同冻结规则版本、价格档位、证据、决策门和风险结论。
ALTER TABLE analysis_reports ADD COLUMN personal_model JSONB;
ALTER TABLE analysis_reports ADD CONSTRAINT chk_personal_model_object
    CHECK (jsonb_typeof(personal_model) = 'object');

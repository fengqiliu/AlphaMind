package com.alphamind.investment;

import com.alphamind.model.dto.AnalysisReportDTO;
import com.alphamind.service.AnalysisHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 从不可变报告派生复核草稿；价格敏感结论和当前风险不沿用。 */
@Service
@RequiredArgsConstructor
public class PersonalReviewReuseService {
    private static final Set<String> FINANCIAL_CHECKS = Set.of(
            "adjustedProfit", "cashRecovery", "inventory", "productRevenue", "recovery");
    private final PersonalModelCatalog catalog;
    private final AnalysisHistoryService history;

    public record Draft(String sourceReportId, String stockCode, String stockName, String modelVersion,
                        LocalDateTime sourceCreatedAt, boolean expired, PersonalModelReview review,
                        List<String> notices) {}

    public static class ModelVersionMismatch extends IllegalArgumentException {
        public ModelVersionMismatch() { super("来源报告与当前模型版本不一致，请重新复核"); }
    }

    public Optional<Draft> latest(String stockCode) {
        var asset = catalog.asset(stockCode);
        return history.getLatestPersonalReview(stockCode).map(report -> {
            validateReport(stockCode, report);
            var source = report.getPersonalModel().evidence();
            var draft = new PersonalModelReview();
            draft.setSourceReportId(report.getId());
            draft.setReviewedAt(source.getReviewedAt());
            draft.setValidUntil(source.getValidUntil());
            draft.setEvidenceSource(source.getEvidenceSource());
            draft.setCounterArgument(source.getCounterArgument());
            draft.setEarnings(source.getEarnings());
            draft.setCashFlow(source.getCashFlow());
            var checks = new LinkedHashMap<String, PersonalModelReview.Decision>();
            if (source.getSpecialChecks() != null) {
                source.getSpecialChecks().forEach((key, value) -> {
                    if (FINANCIAL_CHECKS.contains(key) && asset.specialChecks().containsKey(key)) checks.put(key, value);
                });
            }
            draft.setSpecialChecks(checks);
            LocalDate today = LocalDate.now();
            boolean expired = source.getReviewedAt() == null || source.getReviewedAt().isAfter(today)
                    || source.getValidUntil() == null || source.getValidUntil().isBefore(today)
                    || source.getValidUntil().isBefore(source.getReviewedAt());
            return new Draft(report.getId(), stockCode, asset.name(), report.getPersonalModel().version(),
                    report.getCreatedAt(), expired, draft, List.of(
                    "仅载入财报项、来源与反方文字；原报告没有修改。",
                    "估值、业务逻辑、价值陷阱、择时、杠杆与当前组合数据均需重新确认。",
                    "复核日期和有效期未延长，过期证据仍不能通过。"));
        });
    }

    public void validateSource(String stockCode, PersonalModelReview review) {
        if (review == null || review.getSourceReportId() == null) return;
        var report = history.getReport(review.getSourceReportId())
                .orElseThrow(() -> new IllegalArgumentException("来源报告不存在，请重新载入复核"));
        validateReport(stockCode, report);
    }

    private void validateReport(String stockCode, AnalysisReportDTO report) {
        var assessment = report.getPersonalModel();
        if (!stockCode.equals(report.getStockCode()) || assessment == null || assessment.evidence() == null
                || assessment.asset() == null || !stockCode.equals(assessment.asset().code())) {
            throw new IllegalArgumentException("来源报告与当前标的不匹配");
        }
        var model = catalog.definition();
        if (!model.id().equals(assessment.modelId()) || !model.version().equals(assessment.version())
                || !model.sourceSha256().equals(assessment.sourceSha256())) throw new ModelVersionMismatch();
    }
}

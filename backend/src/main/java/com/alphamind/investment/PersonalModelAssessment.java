package com.alphamind.investment;

import com.alphamind.model.dto.FundamentalsDTO;

import java.time.LocalDate;
import java.util.List;

public record PersonalModelAssessment(String modelId, String version, String versionDate, String sourceSha256,
        PersonalModelDefinition.Asset asset, String level, String action, boolean entryCandidate,
        String priceZone, Double currentPrice, List<Gate> gates, Timing timing,
        List<String> missingEvidence, List<String> blockers, List<String> notes,
        PersonalModelReview evidence, LocalDate assessedOn,
        /** 自动取得的财报/估值参考，冻结在报告中；不参与门判定。旧报告和数据源不可用时为 null。 */
        FundamentalsDTO fundamentals) {
    public record Gate(String id, String label, String status, String detail) {}
    public record Timing(Double dailyRsi14, Double weeklyRsi14, Double atr20, Double atrPricePct,
                         Double trueRange, Double nextBatchPriceCeiling, String dataAsOf) {}

    public PersonalModelAssessment withFundamentals(FundamentalsDTO value) {
        return new PersonalModelAssessment(modelId, version, versionDate, sourceSha256, asset, level, action, entryCandidate,
                priceZone, currentPrice, gates, timing, missingEvidence, blockers, notes, evidence, assessedOn, value);
    }

    public PersonalModelAssessment withNote(String note) {
        var updated = new java.util.ArrayList<>(notes);
        updated.add(note);
        return new PersonalModelAssessment(modelId, version, versionDate, sourceSha256, asset, level, action, entryCandidate,
                priceZone, currentPrice, gates, timing, missingEvidence, blockers, List.copyOf(updated), evidence, assessedOn, fundamentals);
    }
}

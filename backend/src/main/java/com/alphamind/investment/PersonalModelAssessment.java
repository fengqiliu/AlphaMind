package com.alphamind.investment;

import java.time.LocalDate;
import java.util.List;

public record PersonalModelAssessment(String modelId, String version, String versionDate, String sourceSha256,
        PersonalModelDefinition.Asset asset, String level, String action, boolean entryCandidate,
        String priceZone, Double currentPrice, List<Gate> gates, Timing timing,
        List<String> missingEvidence, List<String> blockers, List<String> notes,
        PersonalModelReview evidence, LocalDate assessedOn) {
    public record Gate(String id, String label, String status, String detail) {}
    public record Timing(Double dailyRsi14, Double weeklyRsi14, Double atr20, Double atrPricePct,
                         Double trueRange, Double nextBatchPriceCeiling, String dataAsOf) {}
}

package com.alphamind.investment;

import java.util.List;
import java.util.Map;

public record PersonalModelDefinition(String id, String name, String version, String versionDate,
        String sourceFile, String sourceSha256, String principle, List<Asset> assets) {
    public record Band(int tier, double low, double high) {}
    public record Asset(String code, String name, String category, String theme, List<Band> bands,
            String valuationFocus, String fundamentalFocus, String vetoRisk,
            Map<String, String> specialChecks, boolean provisional) {}
}

package com.alphamind.model.dto;

import com.alphamind.fundamental.provider.FinancialMetrics;
import com.alphamind.fundamental.provider.ValuationMetrics;
import com.alphamind.fundamental.service.FundamentalObservations;
import com.alphamind.fundamental.service.FundamentalResolution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 自动取得的财报与估值快照，随个人模型报告冻结保存。只作 Gate 1/2 的复核参考：
 * {@code observations} 为中性描述，{@code evidenceSummary} 可填入证据来源，都不改变门状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalsDTO {

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private String stockCode;
    private String source;
    /** LIVE / FRESH_CACHE / STALE_CACHE。 */
    private String status;
    private Boolean stale;
    private String warning;
    private LocalDate retrievedOn;
    private ValuationMetrics valuation;
    private FinancialMetrics financials;
    private List<String> observations;
    private String evidenceSummary;
    private String disclaimer;

    public static FundamentalsDTO from(String stockCode, FundamentalResolution resolution, LocalDate today) {
        var snapshot = resolution.snapshot();
        LocalDate retrievedOn = snapshot.retrievedAt() == null ? today : snapshot.retrievedAt().atZone(MARKET_ZONE).toLocalDate();
        return FundamentalsDTO.builder()
                .stockCode(stockCode)
                .source(snapshot.sourceName())
                .status(resolution.status().name())
                .stale(resolution.stale())
                .warning(resolution.warning())
                .retrievedOn(retrievedOn)
                .valuation(snapshot.valuation())
                .financials(snapshot.financials())
                .observations(FundamentalObservations.describe(snapshot.valuation(), snapshot.financials(), today))
                .evidenceSummary(FundamentalObservations.evidenceSummary(snapshot.valuation(), snapshot.financials(), retrievedOn))
                .disclaimer(FundamentalObservations.DISCLAIMER)
                .build();
    }
}

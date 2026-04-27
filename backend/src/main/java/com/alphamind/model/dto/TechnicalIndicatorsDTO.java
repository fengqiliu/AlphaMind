package com.alphamind.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalIndicatorsDTO {
    private MacdDTO macd;
    private RsiDTO rsi;
    private KdjDTO kdj;
    private BollingerDTO bollinger;
    private Integer technicalScore;
    // LLM生成的技术指标解读
    private String aiInterpretation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MacdDTO {
        private Double dif;
        private Double dea;
        private Double histogram;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RsiDTO {
        private Double rsi6;
        private Double rsi12;
        private Double rsi24;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KdjDTO {
        private Double k;
        private Double d;
        private Double j;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BollingerDTO {
        private Double upper;
        private Double middle;
        private Double lower;
    }
}

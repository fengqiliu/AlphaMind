package com.alphamind.model.dto;

import com.alphamind.model.enums.StrategyType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {
    @NotBlank(message = "股票代码不能为空")
    private String stockCode;

    private StrategyType strategy;

    private Boolean enableDebate;

    private String sessionId;
}

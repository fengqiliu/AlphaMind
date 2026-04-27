package com.alphamind.model.dto;

import com.alphamind.model.enums.SignalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSignalDTO {
    private SignalType type;
    private Double entryPrice;
    private Double targetPrice;
    private Double stopLoss;
    private Integer holdingPeriodDays;
    private String rationale;
}

package com.alphamind.model.dto;

import com.alphamind.model.enums.DebatePosition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgmentDTO {
    private DebatePosition finalPosition;
    private ConfidenceDTO confidence;
    private String reasoning;
    private Map<DebatePosition, Integer> voteBreakdown;
    private List<String> riskWarnings;
    private TradeSignalDTO finalSignal;
}

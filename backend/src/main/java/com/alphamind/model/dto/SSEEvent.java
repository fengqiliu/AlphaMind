package com.alphamind.model.dto;

import com.alphamind.model.enums.AgentType;
import com.alphamind.model.enums.AnalysisStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SSEEvent {
    private String event;
    private AnalysisStage stage;
    private String message;
    private AgentType agentType;
    private Object data;

    public static SSEEvent stageEvent(AnalysisStage stage, String message) {
        return SSEEvent.builder()
                .event("stage")
                .stage(stage)
                .message(message)
                .build();
    }

    public static SSEEvent dataEvent(AgentType agentType, Object data) {
        return SSEEvent.builder()
                .event("data")
                .agentType(agentType)
                .data(data)
                .build();
    }

    public static SSEEvent completeEvent() {
        return SSEEvent.builder()
                .event("complete")
                .build();
    }

    public static SSEEvent errorEvent(String message) {
        return SSEEvent.builder()
                .event("error")
                .message(message)
                .build();
    }
}

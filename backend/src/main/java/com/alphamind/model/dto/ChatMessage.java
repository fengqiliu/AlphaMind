package com.alphamind.model.dto;

import com.alphamind.model.enums.AgentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String id;
    private String role;
    private String content;
    private AgentType agentType;
    private String agentName;
    private String modelUsed;
    private LocalDateTime timestamp;
}

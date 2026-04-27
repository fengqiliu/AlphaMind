package com.alphamind.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * 聊天消息实体
 */
@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEntity {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ChatSessionEntity session;

    /** user / assistant / system */
    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /** MARKET / TECHNICAL / SENTIMENT / PORTFOLIO / BULL / BEAR / NEUTRAL / ARBITRATOR */
    @Column(name = "agent_type", length = 20)
    private String agentType;

    @Column(name = "agent_name", length = 50)
    private String agentName;

    @Column(name = "model_used", length = 50)
    private String modelUsed;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

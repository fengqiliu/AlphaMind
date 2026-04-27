package com.alphamind.repository;

import com.alphamind.model.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

    List<ChatSessionEntity> findByUserIdOrderByLastActiveAtDesc(String userId);

    List<ChatSessionEntity> findByStockCodeOrderByLastActiveAtDesc(String stockCode);

    @Modifying
    @Query("UPDATE ChatSessionEntity s SET s.lastActiveAt = :time, s.messageCount = s.messageCount + 1 WHERE s.sessionId = :id")
    void touchSession(@Param("id") String sessionId, @Param("time") OffsetDateTime time);
}

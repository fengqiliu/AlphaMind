package com.alphamind.repository;

import com.alphamind.model.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {

    List<ChatMessageEntity> findBySessionSessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatMessageEntity> findBySessionSessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    long countBySessionSessionId(String sessionId);
}

package com.alphamind.archive;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SentimentArchiveRepository extends JpaRepository<SentimentArchiveEntity, Long> {
}

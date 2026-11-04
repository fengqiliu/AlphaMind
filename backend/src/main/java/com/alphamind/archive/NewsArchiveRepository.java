package com.alphamind.archive;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsArchiveRepository extends JpaRepository<NewsArchiveEntity, Long> {
    boolean existsByStockCodeAndDedupeKey(String stockCode, String dedupeKey);
}

package com.alphamind.repository;

import com.alphamind.model.entity.WatchlistItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistItemRepository extends JpaRepository<WatchlistItemEntity, Long> {

    List<WatchlistItemEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<WatchlistItemEntity> findByUserIdAndStockCode(String userId, String stockCode);

    boolean existsByUserIdAndStockCode(String userId, String stockCode);

    @Modifying
    @Query("DELETE FROM WatchlistItemEntity w WHERE w.userId = :userId AND w.stockCode = :stockCode")
    int deleteByUserIdAndStockCode(@Param("userId") String userId, @Param("stockCode") String stockCode);

    long countByUserId(String userId);
}

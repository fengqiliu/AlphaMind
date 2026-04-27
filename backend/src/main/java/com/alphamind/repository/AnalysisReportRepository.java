package com.alphamind.repository;

import com.alphamind.model.entity.AnalysisReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AnalysisReportRepository extends JpaRepository<AnalysisReportEntity, String> {

    List<AnalysisReportEntity> findTop50ByOrderByCreatedAtDesc();

    List<AnalysisReportEntity> findByStockCodeOrderByCreatedAtDesc(String stockCode);

    Page<AnalysisReportEntity> findByStockCode(String stockCode, Pageable pageable);

    List<AnalysisReportEntity> findByCreatedAtAfterOrderByCreatedAtDesc(OffsetDateTime after);

    @Query("SELECT r FROM AnalysisReportEntity r WHERE r.signalType = :signal ORDER BY r.createdAt DESC")
    List<AnalysisReportEntity> findBySignalType(@Param("signal") String signalType);

    boolean existsByIdAndStockCode(String id, String stockCode);
}

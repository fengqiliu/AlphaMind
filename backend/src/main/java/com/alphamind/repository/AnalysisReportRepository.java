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
import java.util.Optional;

@Repository
public interface AnalysisReportRepository extends JpaRepository<AnalysisReportEntity, String> {

    List<AnalysisReportEntity> findTop50ByOrderByCreatedAtDesc();

    List<AnalysisReportEntity> findByStockCodeOrderByCreatedAtDesc(String stockCode);

    Optional<AnalysisReportEntity> findFirstByStockCodeOrderByCreatedAtDesc(String stockCode);

    @Query(value = """
            SELECT * FROM analysis_reports
            WHERE stock_code = :stockCode AND personal_model IS NOT NULL
              AND (personal_model #>> '{evidence,evidenceSource}') ~ '[^[:space:]　]'
            ORDER BY created_at DESC, id DESC LIMIT 1
            """, nativeQuery = true)
    Optional<AnalysisReportEntity> findLatestPersonalReview(@Param("stockCode") String stockCode);

    Page<AnalysisReportEntity> findByStockCode(String stockCode, Pageable pageable);

    List<AnalysisReportEntity> findByCreatedAtAfterOrderByCreatedAtDesc(OffsetDateTime after);

    @Query("SELECT r FROM AnalysisReportEntity r WHERE r.signalType = :signal ORDER BY r.createdAt DESC")
    List<AnalysisReportEntity> findBySignalType(@Param("signal") String signalType);

    boolean existsByIdAndStockCode(String id, String stockCode);
}

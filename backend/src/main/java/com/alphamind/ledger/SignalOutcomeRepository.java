package com.alphamind.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface SignalOutcomeRepository extends JpaRepository<SignalOutcomeEntity, Long> {

    /** 还有持有期未写入结果的报告（每份报告最多 {@value SignalLedgerService#HORIZON_COUNT} 行）。 */
    @Query(value = """
            SELECT r.id AS id, r.stock_code AS stockCode, CAST(EXTRACT(EPOCH FROM r.created_at) * 1000 AS BIGINT) AS createdAtMillis,
                   COALESCE(r.final_signal_type, r.signal_type) AS signalType,
                   CAST(r.confidence_value AS DOUBLE PRECISION) AS confidenceValue,
                   r.strategy AS strategy, r.analysis_mode AS analysisMode,
                   r.personal_model ->> 'level' AS personalLevel
            FROM analysis_reports r
            WHERE (SELECT count(*) FROM signal_outcomes o WHERE o.report_id = r.id) < 3
            ORDER BY r.created_at
            """, nativeQuery = true)
    List<LedgerCandidate> findPendingCandidates();

    List<SignalOutcomeEntity> findByReportIdIn(Collection<String> reportIds);

    List<SignalOutcomeEntity> findByReportIdOrderByHorizonDays(String reportId);
}

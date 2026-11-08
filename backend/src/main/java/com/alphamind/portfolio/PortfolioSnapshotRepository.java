package com.alphamind.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshotEntity, LocalDate> {
    List<PortfolioSnapshotEntity> findAllByOrderBySnapshotDateAsc();

    /** 没有任何快照时返回 null。 */
    @Query("select max(s.totalValue) from PortfolioSnapshotEntity s")
    Double maxTotalValue();
}

package com.alphamind.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioHealthAlertRepository extends JpaRepository<PortfolioHealthAlertEntity, Long> {
    List<PortfolioHealthAlertEntity> findTop50ByAcknowledgedAtIsNullOrderByTriggeredAtDesc();
    List<PortfolioHealthAlertEntity> findTop50ByOrderByTriggeredAtDesc();
    long countByAcknowledgedAtIsNull();
}

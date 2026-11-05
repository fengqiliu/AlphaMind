package com.alphamind.investment.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceAlertRepository extends JpaRepository<PriceAlertEntity, Long> {
    List<PriceAlertEntity> findTop50ByAcknowledgedAtIsNullOrderByTriggeredAtDesc();
    List<PriceAlertEntity> findTop50ByOrderByTriggeredAtDesc();
    long countByAcknowledgedAtIsNull();
}

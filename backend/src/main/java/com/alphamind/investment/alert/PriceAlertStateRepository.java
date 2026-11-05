package com.alphamind.investment.alert;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceAlertStateRepository extends JpaRepository<PriceAlertStateEntity, String> {}

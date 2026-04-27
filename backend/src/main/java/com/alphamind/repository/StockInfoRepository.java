package com.alphamind.repository;

import com.alphamind.model.entity.StockInfoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockInfoRepository extends JpaRepository<StockInfoEntity, String> {

    List<StockInfoEntity> findByIndustryOrderByStockCode(String industry);

    List<StockInfoEntity> findByStockNameContainingIgnoreCaseOrStockCodeContainingIgnoreCase(
            String name, String code);
}

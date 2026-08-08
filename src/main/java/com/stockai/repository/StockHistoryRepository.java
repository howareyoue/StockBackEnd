package com.stockai.repository;

import com.stockai.entity.StockHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockHistoryRepository
        extends JpaRepository<StockHistory, Long> {

    List<StockHistory> findTop20ByOrderByCreatedAtDesc();

    Optional<StockHistory>
    findTopByStockNameOrderByCreatedAtDesc(String stockName);
}
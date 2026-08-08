package com.stockai.repository;

import com.stockai.entity.RecommendationHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {

    List<RecommendationHistory> findAllByOrderByRecommendTimeDesc();

    List<RecommendationHistory> findAllByOrderByRecommendTimeDesc(Pageable pageable);

    List<RecommendationHistory> findAllByStatus(String status);

    boolean existsByStockCodeAndRecommendTimeBetween(
            String stockCode, LocalDateTime start, LocalDateTime end);
}
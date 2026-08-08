package com.stockai.repository;

import com.stockai.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    List<AiAnalysis> findByAnalysisDate(LocalDate analysisDate);
}
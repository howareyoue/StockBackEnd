package com.stockai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stockCode;

    private String stockName;

    // 예: "매수 고려", "관망", "주의", "분석 실패"
    private String aiAction;

    @Column(columnDefinition = "TEXT")
    private String aiReason;

    @Column(columnDefinition = "TEXT")
    private String newsSummary;

    private LocalDate analysisDate;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
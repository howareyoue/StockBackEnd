package com.stockai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "stock_history")
@Getter
@Setter
public class StockHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String stockCode;   // 추가

    private String stockName;

    private String signal;

    private int score;

    private String reason;

    private double rsi;

    private double macd;

    private double macdSignal;

    private boolean goldenCross;

    private double volumeRate;

    private boolean maAlignment;

    private String candlePattern;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
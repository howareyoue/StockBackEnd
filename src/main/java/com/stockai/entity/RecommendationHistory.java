package com.stockai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "recommendation_history")
@Getter
@Setter
public class RecommendationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 종목코드
    @Column(nullable = false)
    private String stockCode;

    // 종목명
    @Column(nullable = false)
    private String stockName;

    // 추천 신호
    private String signal;

    // 점수
    private Integer score;

    // 추천 당시 가격
    private Double recommendPrice;

    // 최고가
    private Double highPrice;

    // 현재가
    private Double currentPrice;

    // 최고 수익률
    private Double maxProfitRate;

    // 현재 수익률
    private Double profitRate;

    // 3% 달성
    private Boolean hit3 = false;

    // 5% 달성
    private Boolean hit5 = false;

    // SUCCESS / FAIL
    private String status;

    // 추천시간
    private LocalDateTime recommendTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
}
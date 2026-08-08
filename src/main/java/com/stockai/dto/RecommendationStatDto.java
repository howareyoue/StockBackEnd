package com.stockai.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecommendationStatDto {

    // 총 추천 개수
    private long total;

    // 성공 개수
    private long success;

    // 실패 개수
    private long fail;

    // 진행중 개수
    private long wait;

    // 성공률
    private double successRate;

    // 평균 수익률
    private double averageProfit;

    // 평균 최고수익률
    private double averageMaxProfit;

    // 3% 달성률
    private double hit3Rate;

    // 5% 달성률
    private double hit5Rate;
}
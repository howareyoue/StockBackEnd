package com.stockai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StockDto {

    private String stockCode;

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
}
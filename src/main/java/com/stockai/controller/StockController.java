package com.stockai.controller;

import com.stockai.entity.StockHistory;
import com.stockai.repository.StockHistoryRepository;
import com.stockai.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
@CrossOrigin
@RequiredArgsConstructor

public class StockController {

    private final StockService stockService;
    private final StockHistoryRepository stockHistoryRepository;

    // 추천 종목 즉시 조회
    @GetMapping("/recommend")
    public Map<String, Object> recommendStocks() {

        return stockService.getRecommendedStocks();
    }

    // 최신 추천 내역 조회
    @GetMapping("/latest")
    public List<StockHistory> latest() {

        return stockHistoryRepository
                .findTop20ByOrderByCreatedAtDesc();
    }

    // 전체 추천 이력 조회
    @GetMapping("/history")
    public List<StockHistory> history() {

        return stockHistoryRepository.findAll();
    }

    // 서버 상태 확인
    @GetMapping("/")
    public String test() {

        return "SERVER OK";
    }
}
package com.stockai.controller;

import com.stockai.entity.RecommendationHistory;
import com.stockai.scheduler.StockScheduler;
import com.stockai.service.RecommendationBackfillService;
import com.stockai.service.RecommendationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@CrossOrigin
public class RecommendationHistoryController {

    private final RecommendationHistoryService service;
    private final RecommendationBackfillService backfillService;
    private final StockScheduler stockScheduler;

    /**
     * 추천 이력 조회 (최신순, 기본 50건 - 화면 렌더링용)
     */
    @GetMapping
    public List<RecommendationHistory> getHistory(
            @RequestParam(defaultValue = "50") int limit) {
        return service.findRecent(limit);
    }

    /**
     * 추천 통계 조회
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        return service.getStatistics();
    }

    /**
     * 아직 결과를 확인하지 않은 추천 종목 조회
     */
    @GetMapping("/waiting")
    public List<RecommendationHistory> getWaitingList() {
        return service.findWaitingList();
    }

    /**
     * 과거 WAIT 데이터 일괄 백필 (1회성 - 완료까지 시간이 걸릴 수 있음)
     */
    @PostMapping("/backfill")
    public String backfill() {
        return backfillService.backfillWaitingHistory();
    }

    /**
     * 결과 확정 로직 수동 트리거 (20:00 스케줄을 기다리지 않고 지금 바로 실행)
     */
    @PostMapping("/confirm")
    public String confirmNow() {
        stockScheduler.updateRecommendationResults();
        return "결과 확정 로직을 수동으로 실행했습니다. 콘솔 로그를 확인해주세요.";
    }
}
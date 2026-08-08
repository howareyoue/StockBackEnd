package com.stockai.service;

import com.stockai.entity.RecommendationHistory;
import com.stockai.repository.RecommendationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationHistoryService {

    private final RecommendationHistoryRepository repository;

    public RecommendationHistory save(RecommendationHistory history){
        return repository.save(history);
    }

    public List<RecommendationHistory> findAll(){
        return repository.findAllByOrderByRecommendTimeDesc();
    }

    /**
     * 최근 N건만 조회 (화면 렌더링용 - 전체 조회는 무겁고 화면도 못 버팀)
     */
    public List<RecommendationHistory> findRecent(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return repository.findAllByOrderByRecommendTimeDesc(pageable);
    }

    public List<RecommendationHistory> findWaitingList() {
        return findAll().stream()
                .filter(history -> "WAIT".equals(history.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * 추천 통계 계산 - 테이블을 한 번만 조회해서 모든 지표를 계산
     */
    public Map<String, Object> getStatistics() {

        List<RecommendationHistory> all = repository.findAll();

        long total = all.size();

        long success = all.stream()
                .filter(h -> "SUCCESS".equals(h.getStatus()))
                .count();

        long fail = all.stream()
                .filter(h -> "FAIL".equals(h.getStatus()))
                .count();

        long wait = all.stream()
                .filter(h -> "WAIT".equals(h.getStatus()))
                .count();

        long hit3 = all.stream()
                .filter(h -> Boolean.TRUE.equals(h.getHit3()))
                .count();

        long hit5 = all.stream()
                .filter(h -> Boolean.TRUE.equals(h.getHit5()))
                .count();

        double averageProfit = all.stream()
                .mapToDouble(h -> h.getProfitRate() == null ? 0.0 : h.getProfitRate())
                .average()
                .orElse(0.0);

        double averageMaxProfit = all.stream()
                .mapToDouble(h -> h.getMaxProfitRate() == null ? 0.0 : h.getMaxProfitRate())
                .average()
                .orElse(0.0);

        // 소수점 둘째 자리까지 반올림
        averageProfit = Math.round(averageProfit * 100) / 100.0;
        averageMaxProfit = Math.round(averageMaxProfit * 100) / 100.0;

        double successRate = total == 0 ? 0.0 : Math.round((hit3 / (double) total) * 1000) / 10.0;
        double hit3Rate = total == 0 ? 0.0 : Math.round((hit3 / (double) total) * 1000) / 10.0;
        double hit5Rate = total == 0 ? 0.0 : Math.round((hit5 / (double) total) * 1000) / 10.0;

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("fail", fail);
        result.put("wait", wait);
        result.put("successRate", successRate);
        result.put("averageProfit", averageProfit);
        result.put("averageMaxProfit", averageMaxProfit);
        result.put("hit3Rate", hit3Rate);
        result.put("hit5Rate", hit5Rate);

        return result;
    }

}
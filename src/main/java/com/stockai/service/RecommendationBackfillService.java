package com.stockai.service;

import com.stockai.entity.RecommendationHistory;
import com.stockai.repository.RecommendationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationBackfillService {

    private final RecommendationHistoryRepository recommendationHistoryRepository;
    private final ProfitCheckService profitCheckService;

    /**
     * TODO: StockScheduler와 동일하게 실제 공휴일 날짜를 채워야 합니다.
     */
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            // 예시: LocalDate.of(2026, 1, 1)
    );

    public String backfillWaitingHistory() {

        List<RecommendationHistory> waitingList =
                recommendationHistoryRepository.findAllByStatus("WAIT");

        LocalDate today = LocalDate.now();

        int updated = 0;
        int skipped = 0;
        int failed = 0;

        for (RecommendationHistory history : waitingList) {

            LocalDate recommendDate = history.getRecommendTime().toLocalDate();
            LocalDate targetDate = nextTradingDay(recommendDate);

            // 아직 결과 확정일이 안 됐으면 그대로 WAIT 유지
            if (!targetDate.isBefore(today)) {
                skipped++;
                continue;
            }

            Double recommendPrice = history.getRecommendPrice();

            if (recommendPrice == null || recommendPrice <= 0) {
                skipped++;
                continue;
            }

            double[] price = profitCheckService.fetchPriceOnDate(
                    history.getStockCode(), targetDate);

            if (price == null) {
                failed++;
                continue;
            }

            double close = price[0];
            double high = price[2];

            double profitRate =
                    Math.round(((close - recommendPrice) / recommendPrice) * 10000) / 100.0;

            double maxProfitRate =
                    Math.round(((high - recommendPrice) / recommendPrice) * 10000) / 100.0;

            boolean hit3 = maxProfitRate >= 3.0;
            boolean hit5 = maxProfitRate >= 5.0;

            history.setCurrentPrice(close);
            history.setHighPrice(high);
            history.setProfitRate(profitRate);
            history.setMaxProfitRate(maxProfitRate);
            history.setHit3(hit3);
            history.setHit5(hit5);
            history.setStatus(hit3 || hit5 ? "SUCCESS" : "FAIL");

            recommendationHistoryRepository.save(history);
            updated++;

            try {
                // 네이버에 과도한 요청을 보내지 않도록 살짝 텀을 둠
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        }

        return "백필 완료 - 갱신: " + updated + "건, 대기중: " + skipped
                + "건, 실패: " + failed + "건 (전체 WAIT: " + waitingList.size() + "건)";
    }

    private boolean isTradingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY
                && day != DayOfWeek.SUNDAY
                && !HOLIDAYS.contains(date);
    }

    private LocalDate nextTradingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (!isTradingDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }
}
package com.stockai.scheduler;

import com.stockai.entity.RecommendationHistory;
import com.stockai.repository.RecommendationHistoryRepository;
import com.stockai.service.ProfitCheckService;
import com.stockai.service.StockService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class StockScheduler {

    private final StockService stockService;
    private final ProfitCheckService profitCheckService;
    private final RecommendationHistoryRepository recommendationHistoryRepository;

    /**
     * TODO: 실제 한국 공휴일 날짜로 채워야 합니다.
     * (data.go.kr 특일 정보 API 연동으로 자동화하는 것도 가능)
     */
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            // 예시: LocalDate.of(2026, 1, 1)
    );

    /**
     * 평일 09:00 ~ 14:55 사이, 5분마다 실행.
     * StockService.getRecommendedStocks() 내부에서 장 마감/휴장 여부와
     * "오늘 이미 생성된 추천이 있는지"를 다시 판단하므로, 여기서는 그냥
     * 주기적으로 호출만 해주면 된다.
     */
    @Scheduled(cron = "0 0/5 9-14 * * MON-FRI", zone = "Asia/Seoul")
    public void collectStocks() {

        System.out.println(
                "========== 주식 수집 시작 =========="
        );

        stockService.getRecommendedStocks();

        System.out.println(
                "========== 주식 수집 완료 =========="
        );
    }

    /**
     * 15시대(15:00~15:25)만 별도 cron으로 분리.
     * 위 표현식(9-14)이 15시를 포함하지 않으므로, 15:30 마감 전까지만
     * 돌리기 위해 15시 0~25분을 명시적으로 지정한다.
     */
    @Scheduled(cron = "0 0/5 15 * * MON-FRI", zone = "Asia/Seoul")
    public void collectStocksAfternoon() {

        // 15:30 마감 이후 값(15:30~15:55)은 건너뛴다.
        int minute = java.time.LocalTime.now().getMinute();
        if (minute > 25) {
            return;
        }

        System.out.println(
                "========== 주식 수집 시작(오후) =========="
        );

        stockService.getRecommendedStocks();

        System.out.println(
                "========== 주식 수집 완료(오후) =========="
        );
    }

    /**
     * 서버가 켜질 때마다 한 번, 그동안 20:00 스케줄을 놓쳐서
     * 밀려있는 WAIT 건이 있는지 확인하고 즉시 확정한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {

        System.out.println("=== 서버 준비 완료 - 밀린 추천 결과 확정 여부 확인 ===");

        // 애플리케이션 컨텍스트가 완전히 준비된 이후에 실행
        updateRecommendationResults();

        System.out.println("=== 서버 준비 완료 - 초기 종목 크롤링 시작 (백그라운드) ===");

        Thread startupCrawlThread = new Thread(() -> {
            try {
                stockService.forceRefreshRecommendations();
                System.out.println("=== 초기 종목 크롤링 완료 ===");
            } catch (Exception e) {
                System.out.println("=== 초기 종목 크롤링 실패: " + e.getMessage());
                e.printStackTrace();
            }
        }, "startup-stock-crawl");

        startupCrawlThread.start();
    }

    /**
     * 매일 20:00에 실행.
     * 추천일 다음 거래일이 '오늘이거나 이미 지난' WAIT 건들을 확인해서 SUCCESS/FAIL로 확정한다.
     * (targetDate == today로만 비교하면, 특정 날짜에 서버가 안 떠 있었거나 가격 조회가
     *  실패했을 때 해당 건이 영원히 WAIT로 남는 문제가 있어 isAfter 기준으로 변경)
     */
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Seoul")
    public void updateRecommendationResults() {

        LocalDate today = LocalDate.now();

        if (!isTradingDay(today)) {
            System.out.println("=== 오늘은 휴장일이라 결과 확정을 건너뜁니다 ===");
            return;
        }

        System.out.println("========== 추천 결과 확정 시작 ==========");

        List<RecommendationHistory> waitingList =
                recommendationHistoryRepository.findAllByStatus("WAIT");

        for (RecommendationHistory history : waitingList) {

            LocalDate recommendDate = history.getRecommendTime().toLocalDate();
            LocalDate targetDate = nextTradingDay(recommendDate);

            // 확정 대상일이 아직 안 왔으면 스킵, 이미 왔거나 지났으면 처리
            if (targetDate.isAfter(today)) {
                continue;
            }

            double[] price = profitCheckService.fetchLatestPrice(history.getStockCode());

            if (price == null) {
                System.out.println("=== 가격 조회 실패, 다음 실행 때 재시도: " + history.getStockName());
                continue;
            }

            double currentPrice = price[0];
            double highPrice = price[2];
            Double recommendPrice = history.getRecommendPrice();

            if (recommendPrice == null || recommendPrice <= 0) {
                continue;
            }

            double profitRate =
                    Math.round(((currentPrice - recommendPrice) / recommendPrice) * 10000) / 100.0;

            double maxProfitRate =
                    Math.round(((highPrice - recommendPrice) / recommendPrice) * 10000) / 100.0;

            boolean hit3 = maxProfitRate >= 3.0;
            boolean hit5 = maxProfitRate >= 5.0;

            history.setCurrentPrice(currentPrice);
            history.setHighPrice(highPrice);
            history.setProfitRate(profitRate);
            history.setMaxProfitRate(maxProfitRate);
            history.setHit3(hit3);
            history.setHit5(hit5);
            history.setStatus(hit3 || hit5 ? "SUCCESS" : "FAIL");

            recommendationHistoryRepository.save(history);

            System.out.println(
                    history.getStockName()
                            + " 결과 확정 -> " + history.getStatus()
                            + " (수익률 " + profitRate + "%, 최고 " + maxProfitRate + "%)"
            );
        }

        System.out.println("========== 추천 결과 확정 완료 ==========");
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
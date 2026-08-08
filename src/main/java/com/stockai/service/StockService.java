package com.stockai.service;

import com.stockai.entity.StockHistory;
import com.stockai.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import com.stockai.dto.StockDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.stockai.entity.RecommendationHistory;
import com.stockai.repository.RecommendationHistoryRepository;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockHistoryRepository stockHistoryRepository;
    private final RecommendationHistoryRepository recommendationHistoryRepository;

    private static final int MAX_RECOMMENDATIONS = 20;

    // ✅ 서버 배포 환경(JVM 기본 타임존)이 UTC 등으로 설정돼 있어도
    // 항상 한국 시간 기준으로 장 운영시간을 판단하도록 고정
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            "ETF", "ETN", "레버리지", "인버스", "곱버스", "2X", "3X",
            "선물", "KODEX", "TIGER", "KBSTAR", "KOSEF", "ARIRANG",
            "HANARO", "ACE", "SOL", "RISE", "PLUS", "WON", "KIWOOM",
            "KOACT", "TIMEFOLIO", "채권", "회사채", "국채", "국공채",
            "통안채", "특수채", "머니마켓", "KOFR", "액티브", "인덱스",
            "코스피100", "코스피200", "KOSPI100", "KOSPI200", "S&P",
            "TOP10", "TOP30", "NASDAQ", "나스닥", "배당", "파워",
            "마이티", "1Q", "KCGI", "리츠", "REIT", "스팩", "SPAC",
            "TRF", "합성", "홀딩스", "MSCI", "미국", "중국", "일본", "유럽",
            "TREX", "코스닥", "나스닥", "코스피"
    );

    private static final List<String> EXCLUDE_KEYWORDS_UPPER = EXCLUDE_KEYWORDS.stream()
            .map(String::toUpperCase)
            .toList();

    // ✅ 동시에 여러 요청이 들어와도 크롤링/저장이 한 번에 하나씩만 실행되도록 동기화
    // (동시 호출로 인해 exists() 중복 체크가 서로의 커밋 전 상태를 보지 못해 중복 저장되는 문제 방지)
    @Transactional
    public synchronized Map<String, Object> getRecommendedStocks() {

        List<StockDto> stocks;

        LocalDate today = getCurrentDateForRecommendation();
        LocalTime now = LocalTime.now(KST);

        if (shouldUseStoredRecommendations(today, now)) {
            stocks = getStoredRecommendations();
        } else {
            stocks = getTopStocks(today);
            saveStockHistory(stocks);
        }

        return Map.of(
                "market", "KOREA",
                "mode", getMarketMode(),
                "recommendations", stocks
        );
    }

    /**
     * ✅ 관리자용 / 서버 시작 시 강제 새로고침.
     * 장 운영시간/캐시 여부와 무관하게 즉시 새로 크롤링해서 저장한다.
     * (테스트/디버깅 및 서버 기동 직후 즉시 데이터를 채우기 위한 용도)
     */
    @Transactional
    public synchronized Map<String, Object> forceRefreshRecommendations() {

        LocalDate today = getCurrentDateForRecommendation();

        List<StockDto> stocks = getTopStocks(today);
        saveStockHistory(stocks);

        return Map.of(
                "market", "KOREA",
                "mode", "FORCE_REFRESH",
                "recommendations", stocks
        );
    }

    private void saveStockHistory(List<StockDto> stocks) {

        // ✅ 화면 표시용 캐시 테이블이므로 항상 "가장 최신 크롤링 결과 하나"만 남긴다.
        // 지우지 않고 계속 추가만 하면, 서로 다른 시점의 크롤링 배치가 뒤섞여
        // findTop20ByOrderByCreatedAtDesc()로 조회할 때 같은 종목이 여러 번 나올 수 있다.
        stockHistoryRepository.deleteAll();

        List<StockHistory> historyList = stocks.stream()
            .map(stock -> {
                    StockHistory history = new StockHistory();
                    history.setStockCode(stock.getStockCode());
                    history.setStockName(stock.getStockName());
                    history.setSignal(stock.getSignal());
                    history.setScore(stock.getScore());
                    history.setReason(stock.getReason());
                    history.setRsi(stock.getRsi());
                    history.setMacd(stock.getMacd());
                    history.setMacdSignal(stock.getMacdSignal());
                    history.setGoldenCross(stock.isGoldenCross());
                    history.setVolumeRate(stock.getVolumeRate());
                    history.setMaAlignment(stock.isMaAlignment());
                    history.setCandlePattern(stock.getCandlePattern());
                    return history;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        stockHistoryRepository.saveAll(Objects.requireNonNull(historyList));
    }

    boolean shouldUseStoredRecommendations(LocalDate date, LocalTime time) {

        // 장이 닫혀 있으면 저장된 추천을 우선 사용한다.
        // 이때 저장된 히스토리가 없어도 닫힌 시간대의 캐시 사용 여부를 true로 처리한다.
        if (!isMarketOpen(date, time)) {
            return true;
        }

        List<StockHistory> history =
                stockHistoryRepository.findTop20ByOrderByCreatedAtDesc();

        if (history.isEmpty()) {
            return false;
        }

        LocalDate lastRecommendDate =
                history.get(0).getCreatedAt().toLocalDate();

        // 오늘 이미 추천을 생성했다면 그대로 사용
        return lastRecommendDate.equals(date);
    }

    private boolean isMarketOpen(LocalDate date, LocalTime time) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        return !time.isBefore(LocalTime.of(9, 0)) && time.isBefore(LocalTime.of(15, 30));
    }

    private List<StockDto> getStoredRecommendations() {
        return stockHistoryRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toStockDto)
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .toList();
    }

    private StockDto toStockDto(StockHistory history) {
        return new StockDto(
                history.getStockCode(),
                history.getStockName(),
                history.getSignal(),
                history.getScore(),
                history.getReason(),
                history.getRsi(),
                history.getMacd(),
                history.getMacdSignal(),
                history.isGoldenCross(),
                history.getVolumeRate(),
                history.isMaAlignment(),
                history.getCandlePattern()
        );
    }

    LocalDate getCurrentDateForRecommendation() {
        return getCurrentDateForRecommendation(java.time.Clock.system(KST));
    }

    LocalDate getCurrentDateForRecommendation(java.time.Clock clock) {
        return LocalDate.now(clock);
    }

    private List<StockDto> getTopStocks(LocalDate recommendationDate) {

        List<StockDto> stocks = new ArrayList<>();

        // 종목코드 -> 추천 당시 종가(recommendPrice) 매핑 (최종 선정된 종목만 저장할 때 사용)
        Map<String, Double> recommendPriceMap = new HashMap<>();

        // ✅ 동일 종목코드가 여러 번 처리되는 것을 막기 위한 중복 체크용 Set
        Set<String> processedCodes = new HashSet<>();

        try {

            String url = "https://finance.naver.com/sise/sise_rise.naver";

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .header("referer", "https://finance.naver.com")
                    .timeout(10000)
                    .get();

            Elements rows = doc.select(".type_2 tr");

            System.out.println("=== 크롤링 rows 수 : " + rows.size());

            for (Element row : rows) {

                Elements tds = row.select("td");

                if (tds.size() > 1) {

                    Element link = row.selectFirst("a");

                    if (link != null) {

                        String stockName = link.text();
                        String href = link.attr("href");

                        if (!href.contains("=")) {
                            continue;
                        }

                        if (isExcluded(stockName)) {
                            System.out.println("=== 제외 종목 : " + stockName);
                            continue;
                        }

                        String code = href.split("=")[1];

                        // ✅ 이미 처리한 종목코드면 건너뛴다 (같은 종목이 여러 테이블/섹션에 중복 노출되는 문제 방지)
                        if (processedCodes.contains(code)) {
                            continue;
                        }
                        processedCodes.add(code);

                        // ✅ [close, open, high, low, volume] 5개로 확장
                        List<double[]> priceVolume = getPriceAndVolume(code, 120);

                        if (priceVolume.size() < 10) {
                            continue;
                        }

                        List<Double> closes = priceVolume.stream()
                                .map(pv -> pv[0])
                                .toList();

                        List<Double> volumes = priceVolume.stream()
                                .map(pv -> pv[4])
                                .toList();

                        // ✅ RSI: 최신순 데이터를 오래된순으로 뒤집어서 계산
                        List<Double> closesAsc = new ArrayList<>(closes);
                        java.util.Collections.reverse(closesAsc);
                        double rsi = calculateRsi(closesAsc);
                        int rsiScore = getRsiScore(rsi);

                        // ✅ MACD도 오래된순으로 계산
                        double[] macdResult = calculateMacd(closesAsc);
                        double macdValue = macdResult[0];
                        double macdSignalValue = macdResult[1];
                        boolean goldenCross = macdValue > macdSignalValue && macdValue > 0;
                        int macdScore = goldenCross ? 25 : 10;

                        // ✅ 거래량 비율: 오늘 제외한 평균과 비교
                        double volumeRate = calculateVolumeRate(volumes);
                        int volumeScore = calculateVolumeScore(volumeRate);

                        // ✅ 이평선 정배열도 오래된순 기준
                        boolean maAlignment = isMaAlignment(closesAsc);
                        int maScore = maAlignment ? 15 : 5;

                        // ✅ 캔들 패턴: open/high/low/close 사용
                        String candlePattern = detectCandlePattern(priceVolume);
                        int candleScore = candlePattern.equals("없음") ? 0 : 10;

                        int totalScore = rsiScore + macdScore + volumeScore + maScore + candleScore;

                        String signal = getSignal(totalScore, rsi, macdValue);
                        String reason = getReason(rsi, goldenCross, maAlignment, volumeRate, candlePattern);

                        System.out.println(stockName
                                + " RSI: " + rsi
                                + " MACD: " + macdValue
                                + " 거래량증가율: " + volumeRate
                                + " 정배열: " + maAlignment
                                + " 패턴: " + candlePattern
                                + " 총점: " + totalScore
                                + " 신호: " + signal);

                        stocks.add(new StockDto(
                                code,
                                stockName,
                                signal,
                                totalScore,
                                reason,
                                rsi,
                                macdValue,
                                macdSignalValue,
                                goldenCross,
                                volumeRate,
                                maAlignment,
                                candlePattern
                        ));

                        // 추천 당시 종가는 최종 선정 후 저장할 때 쓰기 위해 매핑만 해둔다
                        recommendPriceMap.put(code, closes.get(0));

                    }
                }
            }

            System.out.println("=== 최종 stocks 수 : " + stocks.size());

        } catch (Exception e) {

            System.out.println("=== 크롤링 에러 : " + e.getMessage());
            e.printStackTrace();
        }

        stocks.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        List<StockDto> topStocks = stocks.size() > MAX_RECOMMENDATIONS
                ? stocks.subList(0, MAX_RECOMMENDATIONS)
                : stocks;

        // ✅ 화면에 실제로 노출되는 상위 종목만 DB에 저장
        saveRecommendationHistory(topStocks, recommendPriceMap, recommendationDate);

        return topStocks;
    }

    /**
     * 최종 선정된(상위 20개) 종목만 오늘 하루 기준으로 중복 없이 저장한다.
     */
    private void saveRecommendationHistory(
            List<StockDto> topStocks,
            Map<String, Double> recommendPriceMap,
            LocalDate recommendationDate) {

        LocalDateTime todayStart = recommendationDate.atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        for (StockDto stock : topStocks) {

            Double recommendPrice = recommendPriceMap.get(stock.getStockCode());

            if (recommendPrice == null) {
                continue;
            }

            boolean alreadyRecommendedToday =
                    recommendationHistoryRepository
                            .existsByStockCodeAndRecommendTimeBetween(
                                    stock.getStockCode(), todayStart, todayEnd);

            if (alreadyRecommendedToday) {
                continue;
            }

            RecommendationHistory history = new RecommendationHistory();

            history.setStockCode(stock.getStockCode());
            history.setStockName(stock.getStockName());
            history.setSignal(stock.getSignal());
            history.setScore(stock.getScore());
            history.setRecommendPrice(recommendPrice);
            history.setStatus("WAIT");

            recommendationHistoryRepository.save(history);
        }
    }

    private boolean isExcluded(String stockName) {

        if (stockName == null || stockName.isBlank()) {
            return false;
        }

        String normalized = stockName.trim().toUpperCase();

        if (normalized.endsWith("우")
                || normalized.endsWith("1우")
                || normalized.endsWith("2우")
                || normalized.endsWith("3우")
                || normalized.contains("우B")
                || normalized.contains("우(전환)")
                || normalized.contains("(전환)")) {
            return true;
        }

        return EXCLUDE_KEYWORDS_UPPER.stream()
                .anyMatch(normalized::contains);
    }

    /**
     * ✅ [close, open, high, low, volume] 순서로 저장
     * 네이버 sise_day 컬럼 순서: 날짜(0), 종가(1), 전일비(2), 시가(3), 고가(4), 저가(5), 거래량(6)
     */
    private List<double[]> getPriceAndVolume(String code, int targetCount) throws IOException {

        List<double[]> result = new ArrayList<>();

        int page = 1;

        while (result.size() < targetCount) {

            String url = "https://finance.naver.com/item/sise_day.naver?code=" + code
                    + "&page=" + page;

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .header("referer", "https://finance.naver.com")
                    .timeout(10000)
                    .get();

            Elements rows = doc.select("table.type2 tr");

            int before = result.size();

            for (Element row : rows) {

                Elements tds = row.select("td");

                if (tds.size() >= 7) {

                    try {

                        double close  = Double.parseDouble(tds.get(1).text().replace(",", ""));
                        double open   = Double.parseDouble(tds.get(3).text().replace(",", ""));
                        double high   = Double.parseDouble(tds.get(4).text().replace(",", ""));
                        double low    = Double.parseDouble(tds.get(5).text().replace(",", ""));
                        double volume = Double.parseDouble(tds.get(6).text().replace(",", ""));

                        result.add(new double[]{close, open, high, low, volume});

                    } catch (Exception ignored) {
                    }

                    if (result.size() >= targetCount) {
                        break;
                    }
                }
            }

            if (result.size() == before) {
                break;
            }

            page++;
        }

        return result;
    }

    /**
     * ✅ 오래된순(오름차순) 데이터 기준으로 계산
     */
    private double calculateRsi(List<Double> closesAsc) {

        double gain = 0;
        double loss = 0;

        for (int i = 1; i < closesAsc.size(); i++) {

            double diff = closesAsc.get(i) - closesAsc.get(i - 1);

            if (diff > 0) {
                gain += diff;
            } else {
                loss += Math.abs(diff);
            }
        }

        double avgGain = gain / (closesAsc.size() - 1);
        double avgLoss = loss / (closesAsc.size() - 1);

        if (avgLoss == 0) return 100;

        double rs = avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));

        return Math.max(0, Math.min(100, Math.round(rsi * 10) / 10.0));
    }

    private double[] calculateMacd(List<Double> closesAsc) {

        double ema12 = calculateEma(closesAsc, 12);
        double ema26 = calculateEma(closesAsc, 26);
        double macd = ema12 - ema26;

        List<Double> macdHistory = calculateMacdHistory(closesAsc);
        double signal = macdHistory.size() >= 9
                ? calculateEma(macdHistory, 9)
                : macd;

        return new double[]{
                Math.round(macd * 1000) / 1000.0,
                Math.round(signal * 1000) / 1000.0
        };
    }

    private List<Double> calculateMacdHistory(List<Double> closesAsc) {

        List<Double> macdList = new ArrayList<>();

        for (int i = 25; i < closesAsc.size(); i++) {
            List<Double> subList = closesAsc.subList(0, i + 1);
            double ema12 = calculateEma(subList, 12);
            double ema26 = calculateEma(subList, 26);
            macdList.add(ema12 - ema26);
        }

        return macdList;
    }

    private double calculateEma(List<Double> closesAsc, int period) {

        if (closesAsc.size() < period) {
            return closesAsc.get(closesAsc.size() - 1);
        }

        double multiplier = 2.0 / (period + 1);
        double ema = closesAsc.subList(0, period)
                .stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);

        for (int i = period; i < closesAsc.size(); i++) {
            ema = (closesAsc.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * ✅ 오늘 거래량(index 0)을 평균에서 제외하고 비율 계산
     */
    private double calculateVolumeRate(List<Double> volumes) {

        if (volumes.size() < 2) return 1.0;

        double today = volumes.get(0);

        List<Double> pastVolumes = volumes.subList(1, volumes.size());

        double avg = pastVolumes.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(1);

        if (avg == 0) return 1.0;

        return Math.round((today / avg) * 100) / 100.0;
    }

    private int calculateVolumeScore(double volumeRate) {

        if (volumeRate >= 2.0) return 25;
        if (volumeRate >= 1.5) return 20;
        if (volumeRate >= 1.2) return 15;
        if (volumeRate >= 1.0) return 10;
        return 5;
    }

    /**
     * ✅ 오래된순(오름차순) 기준 이평선 정배열
     */
    private boolean isMaAlignment(List<Double> closesAsc) {

        if (closesAsc.size() < 20) return false;

        int size = closesAsc.size();

        double ma5 = closesAsc.subList(size - 5, size)
                .stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double ma20 = closesAsc.subList(size - 20, size)
                .stream().mapToDouble(Double::doubleValue).average().orElse(0);

        if (closesAsc.size() < 60) {
            return ma5 > ma20;
        }

        double ma60 = closesAsc.subList(size - 60, size)
                .stream().mapToDouble(Double::doubleValue).average().orElse(0);

        return ma5 > ma20 && ma20 > ma60;
    }

    /**
     * ✅ open/high/low/close 모두 활용한 캔들 패턴 감지
     * priceVolume: [close, open, high, low, volume], 최신순(내림차순)
     */
    private String detectCandlePattern(List<double[]> priceVolume) {

        if (priceVolume.size() < 2) return "없음";

        double todayClose = priceVolume.get(0)[0];
        double todayOpen  = priceVolume.get(0)[1];
        double todayHigh  = priceVolume.get(0)[2];
        double todayLow   = priceVolume.get(0)[3];

        double prevClose  = priceVolume.get(1)[0];
        double prevOpen   = priceVolume.get(1)[1];

        double bodySize   = Math.abs(todayClose - todayOpen);
        double totalRange = todayHigh - todayLow;
        double bodyRatio  = totalRange > 0 ? bodySize / totalRange : 0;

        // 장대양봉: 몸통이 전체 범위의 70% 이상이고 3% 이상 상승
        if (todayClose > todayOpen
                && bodyRatio >= 0.7
                && (todayClose - todayOpen) / todayOpen * 100 >= 3.0) {
            return "장대양봉";
        }

        // 상승장악형: 오늘 양봉이 전날 음봉을 완전히 감싸는 패턴
        if (todayClose > todayOpen
                && prevClose < prevOpen
                && todayOpen < prevClose
                && todayClose > prevOpen) {
            return "상승장악형";
        }

        // 돌파형: 전날 종가 위로 1.5% 이상 상승 마감
        if (todayClose > prevClose
                && (todayClose - prevClose) / prevClose * 100 >= 1.5) {
            return "돌파형";
        }

        return "없음";
    }

    private int getRsiScore(double rsi) {

        if (rsi < 20) return 35;
        if (rsi <= 30) return 30;
        if (rsi <= 40) return 20;
        if (rsi <= 50) return 15;
        if (rsi <= 60) return 18;
        if (rsi <= 70) return 22;
        if (rsi < 80) return 8;
        return 5;
    }

    private String getSignal(int score, double rsi, double macd) {

        if (macd < 0) {
            if (score >= 80) return "BUY";
            if (score >= 60) return "HOLD";
            if (rsi >= 70) return "SELL";
            return "HOLD";
        }

        if (score >= 80) return "STRONG BUY";
        if (score >= 60) return "BUY";
        if (rsi >= 70) return "SELL";
        return "HOLD";
    }

    private String getReason(double rsi, boolean goldenCross,
                             boolean maAlignment, double volumeRate,
                             String candlePattern) {

        List<String> reasons = new ArrayList<>();

        if (rsi <= 30) reasons.add("RSI 과매도");
        else if (rsi <= 45) reasons.add("RSI 상승가능");
        else if (rsi >= 70) reasons.add("RSI 과매수");

        if (goldenCross) reasons.add("MACD 골든크로스");
        if (maAlignment) reasons.add("이평선 정배열");
        if (volumeRate >= 1.5) reasons.add("거래량 급증");
        if (!candlePattern.equals("없음")) reasons.add(candlePattern + " 패턴");

        if (reasons.isEmpty()) return "관망 추천";

        return String.join(" / ", reasons);
    }

    private String getMarketMode() {
        return isMarketOpen(LocalDate.now(KST), LocalTime.now(KST)) ? "REALTIME" : "NEXTDAY";
    }
}
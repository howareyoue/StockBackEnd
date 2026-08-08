package com.stockai.service;

import com.stockai.entity.StockHistory;
import com.stockai.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProfitCheckService {

    private final StockHistoryRepository stockHistoryRepository;

    private static final DateTimeFormatter NAVER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * 종목명으로 종목코드 조회
     */
    public Map<String, Object> searchStock(String name) {

        Map<String, Object> result = new HashMap<>();

        try {

            Optional<StockHistory> stockOpt =
                    stockHistoryRepository
                            .findTopByStockNameOrderByCreatedAtDesc(name);

            if (stockOpt.isEmpty()) {

                result.put("success", false);
                result.put("message", "DB에 없는 종목입니다.");

                return result;
            }

            StockHistory stock = stockOpt.get();

            result.put("success", true);
            result.put("stockName", stock.getStockName());
            result.put("code", stock.getStockCode());

        } catch (Exception e) {

            result.put("success", false);
            result.put("message", "검색 실패 : " + e.getMessage());
        }

        return result;
    }

    /**
     * 네이버에서 최신 거래일의 [현재가(종가), 시가, 고가]를 가져온다.
     */
    public double[] fetchLatestPrice(String code) {

        try {

            String url =
                    "https://finance.naver.com/item/sise_day.naver?code="
                            + code;

            Document doc = Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/137.0.0.0 Safari/537.36")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .header("Referer", "https://finance.naver.com")
                    .timeout(15000)
                    .get();

            Elements rows = doc.select("table.type2 tr");

            for (Element row : rows) {

                Elements tds = row.select("td");

                if (tds.size() < 7) {
                    continue;
                }

                try {

                    String currentText =
                            tds.get(1).text().replace(",", "").trim();

                    String openText =
                            tds.get(3).text().replace(",", "").trim();

                    String highText =
                            tds.get(4).text().replace(",", "").trim();

                    if (currentText.isEmpty()
                            || openText.isEmpty()
                            || highText.isEmpty()) {

                        continue;
                    }

                    double currentPrice = Double.parseDouble(currentText);
                    double openPrice = Double.parseDouble(openText);
                    double highPrice = Double.parseDouble(highText);

                    return new double[]{currentPrice, openPrice, highPrice};

                } catch (NumberFormatException e) {
                    continue;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 특정 날짜의 [종가, 시가, 고가]를 가져온다. (과거 데이터 백필용)
     * 여러 페이지를 넘기면서 날짜가 맞는 row를 찾고, targetDate보다 과거로 넘어가면 중단한다.
     */
    public double[] fetchPriceOnDate(String code, LocalDate targetDate) {

        int page = 1;
        int maxPages = 40; // 안전장치 (약 800 거래일치)

        try {

            while (page <= maxPages) {

                String url = "https://finance.naver.com/item/sise_day.naver?code="
                        + code + "&page=" + page;

                Document doc = Jsoup.connect(url)
                        .userAgent(
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/137.0.0.0 Safari/537.36")
                        .header("Accept-Language", "ko-KR,ko;q=0.9")
                        .header("Referer", "https://finance.naver.com")
                        .timeout(15000)
                        .get();

                Elements rows = doc.select("table.type2 tr");

                boolean pastTarget = false;

                for (Element row : rows) {

                    Elements tds = row.select("td");

                    if (tds.size() < 7) {
                        continue;
                    }

                    String dateText = tds.get(0).text().trim();

                    if (dateText.isEmpty()) {
                        continue;
                    }

                    LocalDate rowDate;

                    try {
                        rowDate = LocalDate.parse(dateText, NAVER_DATE_FORMAT);
                    } catch (Exception e) {
                        continue;
                    }

                    if (rowDate.equals(targetDate)) {

                        try {

                            double close = Double.parseDouble(
                                    tds.get(1).text().replace(",", "").trim());

                            double open = Double.parseDouble(
                                    tds.get(3).text().replace(",", "").trim());

                            double high = Double.parseDouble(
                                    tds.get(4).text().replace(",", "").trim());

                            return new double[]{close, open, high};

                        } catch (Exception e) {
                            return null;
                        }
                    }

                    if (rowDate.isBefore(targetDate)) {
                        pastTarget = true;
                    }
                }

                if (pastTarget) {
                    break;
                }

                page++;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 시초가 대비 최고 수익률 확인 (수동 조회용 API - 기존 동작 유지)
     */
    public Map<String, Object> checkProfit(String code) {

        Map<String, Object> result = new HashMap<>();

        double[] price = fetchLatestPrice(code);

        if (price == null || price[1] <= 0) {

            result.put("success", false);
            result.put("message", "시세 데이터를 가져오지 못했습니다.");

            return result;
        }

        double currentPrice = price[0];
        double openPrice = price[1];
        double highPrice = price[2];

        double profitRate =
                ((currentPrice - openPrice) / openPrice) * 100;

        double maxProfitRate =
                ((highPrice - openPrice) / openPrice) * 100;

        profitRate = Math.round(profitRate * 100) / 100.0;
        maxProfitRate = Math.round(maxProfitRate * 100) / 100.0;

        boolean hit3 = maxProfitRate >= 3.0;
        boolean hit5 = maxProfitRate >= 5.0;

        result.put("success", true);
        result.put("code", code);

        result.put("openPrice", openPrice);
        result.put("currentPrice", currentPrice);
        result.put("highPrice", highPrice);

        result.put("profitRate", profitRate);
        result.put("maxProfitRate", maxProfitRate);

        result.put("hit3", hit3);
        result.put("hit5", hit5);

        if (hit5) {

            result.put("status", "🎯 5% 달성!");
            result.put("statusColor", "green");

        } else if (hit3) {

            result.put("status", "✅ 3% 달성!");
            result.put("statusColor", "blue");

        } else if (profitRate > 0) {

            result.put("status", "📈 상승 중");
            result.put("statusColor", "yellow");

        } else {

            result.put("status", "❌ 미달성");
            result.put("statusColor", "red");
        }

        return result;
    }
}
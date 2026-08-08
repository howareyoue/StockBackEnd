package com.stockai.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class NewsService {

    private static final int MAX_NEWS_PER_STOCK = 5;

    /**
     * 네이버 뉴스 검색에서 종목명 기준 최신 뉴스 제목을 가져온다.
     */
    public List<String> getRecentNewsTitles(String stockName) {

        List<String> titles = new ArrayList<>();

        try {
            String query = URLEncoder.encode(stockName, StandardCharsets.UTF_8);
            String url = "https://search.naver.com/search.naver?where=news&query=" + query + "&sort=1";

            // 🔥 [핵심 수정] 네이버 403 Forbidden 차단을 우회하는 실제 윈도우 크롬 브라우저 완벽 위장 설정
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .referrer("https://www.naver.com") // 네이버 메인을 거쳐 들어온 것처럼 위장
                    .timeout(10000)
                    .get();

            Elements items = doc.select("a.news_tit");

            for (Element item : items) {

                String title = item.attr("title");

                if (title.isBlank()) {
                    title = item.text();
                }

                if (!title.isBlank()) {
                    titles.add(title);
                }

                if (titles.size() >= MAX_NEWS_PER_STOCK) {
                    break;
                }
            }

            // 디버깅 편의를 위한 성공 로그 추가
            log.info("뉴스 크롤링 성공 - 종목: {}, 수집된 뉴스 개수: {}개", stockName, titles.size());

        } catch (Exception e) {
            // 차단 에러 발생 시 로그에 상세 원인 스택트레이스까지 남기도록 개선
            log.warn("뉴스 크롤링 실패 - 종목: {}, 원인: {}", stockName, e.getMessage(), e);
        }

        return titles;
    }
}

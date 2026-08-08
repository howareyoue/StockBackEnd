package com.stockai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockai.client.ClaudeApiClient;
import com.stockai.entity.AiAnalysis;
import com.stockai.entity.StockHistory;
import com.stockai.repository.AiAnalysisRepository;
import com.stockai.repository.StockHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final StockHistoryRepository stockHistoryRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final NewsService newsService;
    private final ClaudeApiClient claudeApiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 오늘자 캐시가 있으면 그대로 반환, 없으면 새로 생성한다.
     */
    @Transactional
    public synchronized List<AiAnalysis> getAiAnalysis() {

        LocalDate today = LocalDate.now(KST);

        List<AiAnalysis> cached = aiAnalysisRepository.findByAnalysisDate(today);

        if (!cached.isEmpty()) {
            boolean allFallback = cached.stream().allMatch(analysis ->
                    "분석 실패".equalsIgnoreCase(analysis.getAiAction())
                            || (analysis.getAiReason() != null && analysis.getAiReason().contains("AI 분석 호출 중 오류"))
            );

            if (!allFallback) {
                return cached;
            }

            log.info("오늘자 AI 분석 결과가 모두 폴백이므로 새로 생성합니다.");
        }

        return generateAiAnalysis(today);
    }

    private List<AiAnalysis> generateAiAnalysis(LocalDate today) {

        List<StockHistory> topStocks = stockHistoryRepository.findTop20ByOrderByCreatedAtDesc();

        if (topStocks.isEmpty()) {
            return List.of();
        }

        // 종목별 뉴스 수집
        Map<String, List<String>> newsMap = new HashMap<>();
        for (StockHistory stock : topStocks) {
            try {
                newsMap.put(stock.getStockCode(), newsService.getRecentNewsTitles(stock.getStockName()));
            } catch (Exception e) {
                log.warn("종목 [{}] 뉴스 크롤링 무시 및 빈 값 처리: {}", stock.getStockName(), e.getMessage());
                newsMap.put(stock.getStockCode(), List.of());
            }
        }

        String prompt = buildPrompt(topStocks, newsMap);

        List<AiAnalysis> results;

        try {
            // 수정된 ClaudeApiClient(Gemini) 호출
            String responseText = claudeApiClient.sendMessage(prompt);
            log.info("AI 원본 응답: {}", responseText);
            results = parseResponse(responseText, topStocks, newsMap, today);
        } catch (Exception e) {
            log.error("AI 분석 호출 또는 JSON 파싱 최종 실패: {}", e.getMessage(), e);
            results = buildFallback(topStocks, newsMap, today);
        }

        aiAnalysisRepository.deleteAll(aiAnalysisRepository.findByAnalysisDate(today));
        return aiAnalysisRepository.saveAll(results);
    }

    private String buildPrompt(List<StockHistory> stocks, Map<String, List<String>> newsMap) {

        StringBuilder sb = new StringBuilder();

        sb.append("당신은 대한민국 주식 전문가입니다. 아래 나열된 주식 종목들의 기술적 지표와 최근 뉴스 데이터를 종합 분석하여 ")
          .append("각 종목에 알맞은 투자 행동 지침과 직관적인 핵심 이유를 도출해 주세요.\n\n");

        for (StockHistory stock : stocks) {
            sb.append("- 종목코드: ").append(stock.getStockCode())
              .append(", 종목명: ").append(stock.getStockName())
              .append(", 기술적신호: ").append(stock.getSignal())
              .append(", RSI: ").append(stock.getRsi())
              .append(", MACD: ").append(stock.getMacd())
              .append(", 거래량비율: ").append(stock.getVolumeRate())
              .append(", 최근뉴스: ")
              .append(String.join(" | ", newsMap.getOrDefault(stock.getStockCode(), List.of("관련 뉴스 없음"))))
              .append("\n");
        }

        // Gemini는 마크다운을 씌우는 성향이 강하므로 지시 형식을 더 명확히 교정
        sb.append("\n[출력 조건]\n")
          .append("1. 반드시 아래 예시와 같은 대괄호로 시작하는 유효한 표준 JSON 배열 구조(JSON Array) 단 하나만 출력하세요.\n")
          .append("2. 마크다운 기호(```json 또는 ```)를 사용하지 마세요. 앞뒤로 인사말이나 설명을 추가하면 파싱 에러가 발생하니 순수 JSON 데이터만 출력하세요.\n\n")
          .append("[응답 JSON 예시 구조]\n")
          .append("[{\"stockCode\":\"068270\",\"action\":\"매수 고려\",\"reason\":\"RSI가 과매도 구간을 탈출 중이며 최근 호재 뉴스가 집중되어 긍정적입니다.\"},")
          .append("{\"stockCode\":\"005930\",\"action\":\"관망\",\"reason\":\"기술적 신호가 정체 상태이며 단기 모멘텀이 부족합니다.\"}]");

        return sb.toString();
    }

    private List<AiAnalysis> parseResponse(
            String responseText,
            List<StockHistory> stocks,
            Map<String, List<String>> newsMap,
            LocalDate today) throws Exception {

        if (responseText == null || responseText.isBlank()) {
            throw new IllegalArgumentException("AI 응답 값이 비어있습니다.");
        }

        // Gemini가 끝내 마크다운 코드 블록 포맷을 붙여 반환할 경우를 대비한 유연한 텍스트 가공 로직
        String cleaned = responseText.trim();
        if (cleaned.contains("[") && cleaned.contains("]")) {
            // 대괄호 외부의 앞뒤 찌꺼기(설명글 등)를 전부 잘라내고 순수 배열 스크립트만 추출
            int startIndex = cleaned.indexOf("[");
            int endIndex = cleaned.lastIndexOf("]") + 1;
            cleaned = cleaned.substring(startIndex, endIndex);
        }

        // 정규식을 통한 마크다운 식별자 2차 완벽 제거
        cleaned = cleaned.replaceAll("```json", "")
                         .replaceAll("```", "")
                         .trim();

        JsonNode rootNode = objectMapper.readTree(cleaned);

        JsonNode arrayNode = null;
        if (rootNode.isArray()) {
            arrayNode = rootNode;
        } else {
            if (rootNode.has("items") && rootNode.get("items").isArray()) {
                arrayNode = rootNode.get("items");
            } else {
                for (JsonNode child : rootNode) {
                    if (child != null && child.isArray()) {
                        arrayNode = child;
                        break;
                    }
                }
            }
        }

        if (arrayNode == null) {
            throw new RuntimeException("AI 응답에서 JSON 배열을 찾을 수 없습니다. 원본: " + cleaned);
        }

        Map<String, StockHistory> stockByCode = new HashMap<>();
        for (StockHistory s : stocks) {
            stockByCode.put(s.getStockCode(), s);
        }

        List<AiAnalysis> results = new ArrayList<>();

        for (JsonNode node : arrayNode) {

            // 유연한 키 추출: stockCode, stock_code, code, symbol, 종목코드 등
            String code = "";
            String[] codeKeys = new String[]{"stockCode", "stock_code", "code", "symbol", "종목코드"};
            for (String k : codeKeys) {
                String v = node.path(k).asText("").trim();
                if (!v.isBlank()) {
                    code = v;
                    break;
                }
            }

            // 숫자만 남겨서 매칭 시도
            String numericCode = code.replaceAll("[^0-9]", "");

            StockHistory stock = null;
            if (!numericCode.isBlank()) {
                stock = stockByCode.get(numericCode);
            }
            if (stock == null && !code.isBlank()) {
                stock = stockByCode.get(code);
            }

            // 이름으로도 매칭 시도
            if (stock == null) {
                String nameCandidate = node.path("stockName").asText("").trim();
                if (!nameCandidate.isBlank()) {
                    for (StockHistory s : stocks) {
                        if (nameCandidate.equalsIgnoreCase(s.getStockName())) {
                            stock = s;
                            break;
                        }
                    }
                }
            }

            if (stock == null) {
                continue;
            }

            AiAnalysis analysis = new AiAnalysis();

            String finalCode = (!numericCode.isBlank()) ? numericCode : code;
            analysis.setStockCode(finalCode);
            analysis.setStockName(stock.getStockName());

            // 유연한 action/reason 키 추출
            String action = "";
            String[] actionKeys = new String[]{"action", "recommendation", "aiAction", "권고", "recommend"};
            for (String k : actionKeys) {
                String v = node.path(k).asText("").trim();
                if (!v.isBlank()) { action = v; break; }
            }

            String reason = "";
            String[] reasonKeys = new String[]{"reason", "explain", "aiReason", "설명", "detail", "comment"};
            for (String k : reasonKeys) {
                String v = node.path(k).asText("").trim();
                if (!v.isBlank()) { reason = v; break; }
            }

            if (action.isBlank()) { action = "관망"; }
            if (reason.isBlank()) { reason = "지표 및 기술적 분석 요망"; }

            analysis.setAiAction(action);
            analysis.setAiReason(reason);

            List<String> news = newsMap.getOrDefault(finalCode, newsMap.getOrDefault(code, List.of()));
            List<String> filtered = new ArrayList<>();
            for (String n : news) {
                if (n != null && !n.isBlank()) filtered.add(n.trim());
            }
            if (filtered.isEmpty()) filtered = List.of("관련 뉴스 없음");

            analysis.setNewsSummary(String.join(" | ", filtered));
            analysis.setAnalysisDate(today);

            results.add(analysis);
        }

        if (results.isEmpty()) {
            throw new RuntimeException("파싱된 유효한 종목 데이터 결과가 없습니다.");
        }

        return results;
    }

    private List<AiAnalysis> buildFallback(
            List<StockHistory> stocks,
            Map<String, List<String>> newsMap,
            LocalDate today) {

        List<AiAnalysis> results = new ArrayList<>();

        for (StockHistory stock : stocks) {

            AiAnalysis analysis = new AiAnalysis();
            analysis.setStockCode(stock.getStockCode());
            analysis.setStockName(stock.getStockName());
            String signal = stock.getSignal();
            analysis.setAiAction(signal != null && !signal.isBlank() ? signal : "관망");
            analysis.setAiReason("AI 분석 호출 중 오류가 발생했습니다. 기술적 신호(" + (signal != null ? signal : "없음") + ")를 참고하세요.");

            List<String> news = newsMap.getOrDefault(stock.getStockCode(), List.of());
            List<String> filtered = new ArrayList<>();
            for (String n : news) {
                if (n != null && !n.isBlank()) filtered.add(n.trim());
            }
            if (filtered.isEmpty()) filtered = List.of("관련 뉴스 없음");
            analysis.setNewsSummary(String.join(" | ", filtered));
            analysis.setAnalysisDate(today);

            results.add(analysis);
        }

        return results;
    }
}

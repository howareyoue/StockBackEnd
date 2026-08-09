package com.stockai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class ClaudeApiClient {

    // ✅ Spring의 ${...} 플레이스홀더 대신 System.getenv로 직접 읽는다.
    // Railway의 Railpack 빌더가 application.properties 안의 ${GOOGLE_API_KEY} 패턴을
    // "빌드 시점에 필요한 secret"으로 잘못 인식해서 빌드 자체를 막는 문제를 피하기 위함.
    private final String apiKey = System.getenv("GOOGLE_API_KEY");

    private final String model = System.getenv().getOrDefault("GOOGLE_API_MODEL", "gemini-3.5-flash");

    // ✅ 실제 Gemini generateContent 엔드포인트 형식
    // {model} 자리에 모델명, key 쿼리 파라미터로 API 키를 전달해야 함
    private static final String API_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 프롬프트를 보내고 텍스트 응답을 반환한다. (Google Gemini 무료 티어 연동)
     */
    public String sendMessage(String prompt) throws Exception {

        // 1. Google Gemini API 규격에 맞는 JSON 바디 구조화
        // 구조: {"contents": [{"parts": [{"text": "프롬프트 본문"}]}]}
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        ));

        // 2. 동적으로 모델명과 API 키가 결합된 URL 생성
        String fullUrl = String.format(API_URL_TEMPLATE, model, apiKey);

        // 3. HTTP 요청 구성 (Gemini는 헤더가 아니라 URL 쿼리 파라미터로 키를 인증함)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // 4. 요청 송신 및 응답 수신
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 에러 발생 시 명확하게 상태 코드와 응답 본문 출력
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Gemini API 호출 실패 - status: " + response.statusCode()
                            + ", body: " + response.body());
        }

        // 5. 구글 응답 JSON 파싱
        // 구조: candidates[0].content.parts[0].text 추출
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode candidates = root.path("candidates");

        StringBuilder text = new StringBuilder();
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    text.append(part.path("text").asText());
                }
            }
        }

        if (text.isEmpty()) {
            // 응답은 200인데 candidates가 비어있는 경우 (안전 필터 차단 등)
            throw new RuntimeException("Gemini 응답에 텍스트가 없습니다. 원본: " + response.body());
        }

        return text.toString();
    }
}
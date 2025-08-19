package com.example.ocrproject.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-pro-002:generateContent?key=";



    private final ObjectMapper mapper = new ObjectMapper();

    public String extractItemsFromText(String ocrText) {
        try {
            String prompt = """
                    다음은 OCR로 추출된 영수증 텍스트입니다. 이 텍스트에서 '요리에 사용할 수 있는 재료명'만 추출하세요.
                    
                    지침:
                    - 가격, 수량, 날짜, 결제 정보, 매장명, 광고 문구, 카테고리 표기는 모두 제외합니다.
                    - 브랜드명/상품명 수식(예: 울월스, 존 웨스트 등)은 제거하고 '재료명'만 남깁니다.
                    - 가공 식품이라도 요리에 쓰일 수 있으면 하나의 재료로 묶어 표기합니다. (예: 딸기 요거트, 크림치즈 베이글)
                    - 불용어/형용 표현(클래식, 라이트, 풀팻 등)은 제거하고 핵심 재료명만 남깁니다.
                    - 출력 형식은 '한 줄'에 '쉼표로 구분된 순수 재료명'만 포함합니다.
                    - 절대 다른 문구, 설명, 번호, 불릿, 따옴표, 마크다운을 넣지 마세요.
                    - 예시 출력: 사과, 양파, 햄, 치즈, 딸기 요거트

                    OCR 텍스트:
""" + ocrText;

            // 요청 JSON 구성
            ObjectNode rootNode = mapper.createObjectNode();
            ArrayNode contents = mapper.createArrayNode();
            ObjectNode contentNode = mapper.createObjectNode();
            ArrayNode parts = mapper.createArrayNode();
            ObjectNode partText = mapper.createObjectNode();
            partText.put("text", prompt);
            parts.add(partText);
            contentNode.set("parts", parts);
            contents.add(contentNode);
            rootNode.set("contents", contents);

            // HTTP 요청 보내기
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(rootNode)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            System.out.println("Gemini 응답: " + responseBody); // 👉 로그 출력

            JsonNode json = mapper.readTree(responseBody);

            // ✅ candidates 존재 여부 체크
            JsonNode candidates = json.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
                throw new RuntimeException("Gemini API 응답 오류: candidates가 없음");
            }

            JsonNode content = candidates.get(0).path("content");
            if (!content.has("parts")) {
                throw new RuntimeException("Gemini API 응답 오류: content 안에 parts가 없음");
            }

            JsonNode partsArray = content.path("parts");
            if (!partsArray.isArray() || partsArray.size() == 0) {
                throw new RuntimeException("Gemini API 응답 오류: parts 배열이 비어 있음");
            }

            JsonNode textNode = partsArray.get(0).path("text");
            return textNode.asText();

        } catch (Exception e) {
            throw new RuntimeException("Gemini API : " + e.getMessage(), e);
        }
    }

    /**
     * Translate arbitrary English text to Korean. Output must be ONLY the translated Korean text
     * without extra explanations, quotes, or markdown.
     */
    public String translateToKorean(String text) {
        if (text == null || text.isBlank()) return text;
        try {
            String prompt = """
                    아래 영어 텍스트를 자연스러운 한국어로 번역하세요.\n
                    규칙:\n
                    - 출력에는 번역문만 포함하세요.\n
                    - 설명, 따옴표, 번호, 불릿, 마크다운을 포함하지 마세요.\n
                    
                    영어 텍스트:\n
                    """ + text;

            ObjectNode rootNode = mapper.createObjectNode();
            ArrayNode contents = mapper.createArrayNode();
            ObjectNode contentNode = mapper.createObjectNode();
            ArrayNode parts = mapper.createArrayNode();
            ObjectNode partText = mapper.createObjectNode();
            partText.put("text", prompt);
            parts.add(partText);
            contentNode.set("parts", parts);
            contents.add(contentNode);
            rootNode.set("contents", contents);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(rootNode)))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            JsonNode json = mapper.readTree(responseBody);
            JsonNode candidates = json.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
                throw new RuntimeException("Gemini API : candidates");
            }
            JsonNode content = candidates.get(0).path("content");
            JsonNode partsArray = content.path("parts");
            if (!partsArray.isArray() || partsArray.size() == 0) {
                throw new RuntimeException("Gemini API : parts ");
            }
            return partsArray.get(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Gemini : " + e.getMessage(), e);
        }
    }
}
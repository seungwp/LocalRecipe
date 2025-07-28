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
                    다음은 OCR로 추출된 영수증 텍스트입니다. 이 텍스트에서 ‘요리에 사용할 수 있는 재료’만 뽑아주세요.\s
                    
                    - 가격, 날짜, 결제 정보, 매장명 등은 제외해주세요. \s
                    - ‘딸기 요거트’, ‘크림치즈 베이글’처럼 하나의 항목으로 구성된 가공 식품은 **하나의 재료로 묶어서** 인식해주세요. \s
                    - 음식 재료에 해당하지 않는 상품은 제외해주세요. \s
                    - 결과는 ‘한글’, ‘쉼표로 구분된 단어 리스트’ 형태로만 출력해주세요. 예: 사과, 양파, 햄, 치즈, 딸기 요거트
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
            throw new RuntimeException("Gemini API 호출 실패: " + e.getMessage(), e);
        }
    }
}
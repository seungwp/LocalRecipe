package com.example.ocrproject.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecipeApiService {

    private static final String BASE = "https://www.themealdb.com/api/json/v1/1/";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    public List<Map<String, Object>> fetchRecipesByPrimaryIngredients(List<String> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) return List.of();
        // Use first 1~2 ingredients as primary query terms
        List<String> primary = ingredients.size() >= 2 ? ingredients.subList(0, 2) : new ArrayList<>(ingredients);

        // Collect meal IDs from filter by ingredient, then lookup details
        Set<String> mealIds = new LinkedHashSet<>();
        for (String ing : primary) {
            String qIng = normalizeIngredientForApi(ing);
            try {
                String q = BASE + "filter.php?i=" + url(qIng);
                HttpRequest req = HttpRequest.newBuilder(URI.create(q)).GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200 || res.body() == null || res.body().isEmpty()) continue;
                JsonNode root = om.readTree(res.body());
                JsonNode meals = root.get("meals");
                if (meals != null && meals.isArray()) {
                    for (JsonNode m : meals) {
                        JsonNode id = m.get("idMeal");
                        if (id != null && !id.isNull()) mealIds.add(id.asText());
                    }
                }
            } catch (Exception ignored) { }
        }

        if (mealIds.isEmpty()) return List.of();

        // Lookup each meal for full details and map to our schema
        List<Map<String, Object>> out = new ArrayList<>();
        for (String id : mealIds.stream().limit(15).collect(Collectors.toList())) {
            try {
                String q = BASE + "lookup.php?i=" + id;
                HttpRequest req = HttpRequest.newBuilder(URI.create(q)).GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200 || res.body() == null || res.body().isEmpty()) continue;
                JsonNode root = om.readTree(res.body());
                JsonNode meals = root.get("meals");
                if (meals == null || !meals.isArray() || meals.size() == 0) continue;
                JsonNode meal = meals.get(0);

                String name = optText(meal, "strMeal");
                String instructions = optText(meal, "strInstructions");

                // Collect up to 20 ingredients
                List<String> need = new ArrayList<>();
                for (int i = 1; i <= 20; i++) {
                    String ing = optText(meal, "strIngredient" + i);
                    String measure = optText(meal, "strMeasure" + i);
                    if (ing == null || ing.isBlank()) continue;
                    String entry = measure != null && !measure.isBlank() ? (ing + " " + measure) : ing;
                    need.add(entry.trim());
                }

                // 한국어 변환 (간단 사전 기반)
                String nameKo = toKorean(name);
                String descKo = instructions; // keep original to avoid translation latency
                List<String> needKo = toKoreanList(need);

                Map<String, Object> m = new HashMap<>();
                m.put("name", nameKo);
                m.put("need", needKo);
                m.put("desc", descKo);
                m.put("matchCount", 0); // will be scored in controller
                out.add(m);
            } catch (Exception ignored) { }
        }
        return out;
    }

    private static String url(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String normalizeIngredientForApi(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        // Simple ko->en mapping for common items
        Map<String, String> m = Map.ofEntries(
                Map.entry("계란", "egg"),
                Map.entry("달걀", "egg"),
                Map.entry("밥", "rice"),
                Map.entry("쌀", "rice"),
                Map.entry("김치", "kimchi"),
                Map.entry("양파", "onion"),
                Map.entry("감자", "potato"),
                Map.entry("당근", "carrot"),
                Map.entry("사과", "apple"),
                Map.entry("치즈", "cheese"),
                Map.entry("베이컨", "bacon"),
                Map.entry("빵", "bread"),
                Map.entry("토마토", "tomato"),
                Map.entry("참치", "tuna"),
                Map.entry("요거트", "yogurt"),
                Map.entry("우유", "milk")
        );
        // if exact key exists use it; otherwise return original
        return m.getOrDefault(s, raw);
    }

    private static String optText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    // --- Simple term-based translator to Korean for UI-only localization ---
    private static String toKorean(String s) {
        if (s == null || s.isBlank()) return s;
        String out = s;
        // Common ingredients
        out = out.replaceAll("(?i)\\begg(s)?\\b", "계란");
        out = out.replaceAll("(?i)\\bonion(s)?\\b", "양파");
        out = out.replaceAll("(?i)\\bgarlic\\b", "마늘");
        out = out.replaceAll("(?i)\\bchicken\\b", "닭고기");
        out = out.replaceAll("(?i)\\bbeef\\b", "소고기");
        out = out.replaceAll("(?i)\\bpork\\b", "돼지고기");
        out = out.replaceAll("(?i)\\brice\\b", "밥");
        out = out.replaceAll("(?i)\\bnoodles?\\b", "면");
        out = out.replaceAll("(?i)\\btomato(es)?\\b", "토마토");
        out = out.replaceAll("(?i)\\bpotato(es)?\\b", "감자");
        out = out.replaceAll("(?i)\\bcarrot(s)?\\b", "당근");
        out = out.replaceAll("(?i)\\bsoy sauce\\b", "간장");
        out = out.replaceAll("(?i)\\bsalt\\b", "소금");
        out = out.replaceAll("(?i)\\bpepper\\b", "후추");
        out = out.replaceAll("(?i)\\bsugar\\b", "설탕");
        out = out.replaceAll("(?i)\\bmilk\\b", "우유");
        out = out.replaceAll("(?i)\\bbutter\\b", "버터");
        out = out.replaceAll("(?i)\\bcheese\\b", "치즈");
        out = out.replaceAll("(?i)\\byogurt\\b", "요거트");
        out = out.replaceAll("(?i)\\btuna\\b", "참치");
        out = out.replaceAll("(?i)\\bbread\\b", "빵");
        out = out.replaceAll("(?i)\\bwater\\b", "물");
        out = out.replaceAll("(?i)\\boil\\b", "기름");
        out = out.replaceAll("(?i)\\bsesame oil\\b", "참기름");
        out = out.replaceAll("(?i)\\bgreen onion(s)?\\b", "대파");

        // Dish name hints
        out = out.replaceAll("(?i)\\bbulgogi\\b", "불고기");
        out = out.replaceAll("(?i)\\bkimchi\\b", "김치");
        out = out.replaceAll("(?i)\\bkimbap|gimbap\\b", "김밥");
        out = out.replaceAll("(?i)\\bramen|ramen\\b", "라면");
        out = out.replaceAll("(?i)\\bbibimbap\\b", "비빔밥");
        out = out.replaceAll("(?i)\\btteokbokki|topokki\\b", "떡볶이");

        return out;
    }

    private static List<String> toKoreanList(List<String> list) {
        if (list == null) return null;
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) out.add(toKorean(s));
        return out;
    }
}

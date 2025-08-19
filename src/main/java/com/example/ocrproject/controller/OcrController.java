package com.example.ocrproject.controller;

import com.example.ocrproject.service.GoogleOcrService;
import com.example.ocrproject.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final GoogleOcrService ocrService;
    private final GeminiService geminiService;

    public OcrController(GoogleOcrService ocrService, GeminiService geminiService) {
        this.ocrService = ocrService;
        this.geminiService = geminiService;
    }

    @PostMapping
    public ResponseEntity<String> extractItems(@RequestParam MultipartFile file) {
        String ocrText = ocrService.extractText(file);
        String result = geminiService.extractItemsFromText(ocrText);
        return ResponseEntity.ok(result);
    }

    // New endpoint: returns JSON { ingredients: [...], recipes: [...] } with hardcoded suggestions
    @PostMapping("/recommend")
    public ResponseEntity<Map<String, Object>> extractAndRecommend(@RequestParam MultipartFile file) {
        String ocrText = ocrService.extractText(file);

        // 1) Extract ingredients from either Gemini result (comma-separated) or simple OCR heuristics
        List<String> ingredients = new ArrayList<>();
        try {
            String ai = geminiService.extractItemsFromText(ocrText);
            ingredients = parseCommaSeparated(ai);
        } catch (Exception ignored) {
            // If Gemini fails or not configured, fall back to naive extraction
            ingredients = naiveExtract(ocrText);
        }

        // 2) Only use hardcoded recipes for performance
        List<Map<String, Object>> recipes = recommendFromHardcoded(ingredients);
        // Limit to top 5
        if (recipes.size() > 5) {
            recipes = new ArrayList<>(recipes.subList(0, 5));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("ingredients", ingredients);
        body.put("recipes", recipes);
        return ResponseEntity.ok(body);
    }

    private static List<String> parseCommaSeparated(String text) {
        if (text == null) return List.of();
        String cleaned = text.replaceAll("[\n\r]+", ",");
        String[] parts = cleaned.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = p.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return dedupLowerPreserve(out);
    }

    private static List<String> naiveExtract(String text) {
        if (text == null) return List.of();
        String lower = text.toLowerCase();
        String[][] keywords = new String[][]{
                {"계란", "달걀", "egg"},
                {"밥", "쌀", "rice"},
                {"김치", "kimchi"},
                {"양파", "onion"},
                {"감자", "potato"},
                {"당근", "carrot"},
                {"사과", "apple"},
                {"치즈", "cheese"},
                {"베이컨", "bacon"},
                {"빵", "bread"},
                {"토마토", "tomato"},
                {"참치", "tuna"},
                {"요거트", "yogurt"},
                {"우유", "milk"},
                {"오트", "oat"},
        };
        Set<String> found = new LinkedHashSet<>();
        for (String[] group : keywords) {
            for (String k : group) {
                if (lower.contains(k)) { found.add(group[0]); break; }
            }
        }
        return new ArrayList<>(found);
    }

    private static List<Map<String, Object>> recommendFromHardcoded(List<String> ingredients) {
        // Expanded hardcoded catalog (popular Korean dishes)
        List<Recipe> db = List.of(
                new Recipe("김치볶음밥", List.of("김치", "밥", "계란", "대파"), "팬에 기름을 두르고 김치를 볶다가 밥과 계란을 넣고 간장으로 간하여 볶습니다."),
                new Recipe("감자조림", List.of("감자", "양파", "간장", "설탕"), "감자를 깍둑썰기하여 양파와 함께 간장양념으로 졸입니다."),
                new Recipe("에그 토스트", List.of("빵", "계란", "치즈", "버터"), "빵 위에 계란과 치즈를 올려 바삭하게 구워냅니다."),
                new Recipe("참치샌드위치", List.of("빵", "참치", "토마토", "양파"), "참치와 토마토, 양파를 넣어 상큼하게 만든 샌드위치."),
                new Recipe("요거트 샐러드", List.of("요거트", "사과", "견과류"), "사과와 견과류를 요거트와 버무립니다."),
                new Recipe("베이컨 에그 롤", List.of("베이컨", "계란"), "베이컨에 달걀물을 말아 구워 간단한 반찬으로 제공합니다."),
                new Recipe("토마토 파스타", List.of("토마토", "양파", "마늘"), "양파와 마늘을 볶아 토마토 소스를 만들고 파스타와 버무립니다."),
                new Recipe("감자 계란 샐러드", List.of("감자", "계란", "마요네즈"), "삶은 감자와 계란을 마요네즈와 버무려 샐러드로 냅니다."),
                new Recipe("사과 요거트 파르페", List.of("사과", "요거트"), "사과와 요거트, 그래놀라를 층층이 담아냅니다."),
                new Recipe("베이컨 토마토 파니니", List.of("빵", "베이컨", "토마토", "치즈"), "베이컨과 토마토, 치즈를 넣어 눌러 구운 파니니."),
                new Recipe("양파 계란덮밥", List.of("양파", "계란", "밥", "간장"), "양파를 볶아 간장소스를 만들고 계란과 함께 밥 위에 올립니다."),
                new Recipe("참치마요 덮밥", List.of("참치", "밥", "마요네즈"), "참치와 마요네즈를 섞어 밥 위에 올리는 간단한 덮밥."),
                new Recipe("토마토 달걀 볶음", List.of("토마토", "계란", "대파"), "중식 스타일로 토마토와 계란을 부드럽게 볶아냅니다."),

                // Korean favorites
                new Recipe("김치찌개", List.of("김치", "돼지고기", "두부", "대파"), "돼지고기를 볶다가 김치를 넣고 끓인 뒤 두부와 파를 넣어 마무리합니다."),
                new Recipe("된장찌개", List.of("된장", "두부", "양파", "애호박"), "멸치육수에 된장을 풀고 두부와 채소를 넣어 끓입니다."),
                new Recipe("순두부찌개", List.of("순두부", "계란", "돼지고기", "고춧가루"), "고기를 볶아 양념을 낸 뒤 순두부를 넣고 끓여 계란으로 마무리합니다."),
                new Recipe("부대찌개", List.of("소시지", "햄", "김치", "두부", "라면"), "햄과 소시지를 김치와 함께 끓이고 라면사리를 넣어 마무리합니다."),
                new Recipe("제육볶음", List.of("돼지고기", "고추장", "양파", "대파"), "고추장 양념에 돼지고기와 채소를 볶아 매콤하게 완성합니다."),
                new Recipe("불고기", List.of("소고기", "양파", "간장", "설탕", "참기름"), "간장양념에 재운 소고기를 양파와 함께 달달하게 볶습니다."),
                new Recipe("비빔밥", List.of("밥", "계란", "시금치", "고사리", "고추장"), "밥 위에 나물과 계란을 올리고 고추장을 넣어 비벼 먹습니다."),
                new Recipe("잡채", List.of("당면", "소고기", "양파", "당근", "시금치"), "재료를 각각 볶아 간장양념과 함께 당면에 버무립니다."),
                new Recipe("떡볶이", List.of("떡", "고추장", "어묵", "대파"), "떡과 어묵을 고추장 소스에 졸여 매콤달콤하게 완성합니다."),
                new Recipe("라볶이", List.of("떡", "라면", "어묵", "고추장"), "떡볶이에 라면사리를 넣어 더욱 푸짐하게 즐깁니다."),
                new Recipe("김치전", List.of("김치", "부침가루", "대파"), "부침가루 반죽에 김치를 넣어 바삭하게 부칩니다."),
                new Recipe("해물파전", List.of("부침가루", "오징어", "새우", "대파"), "대파 듬뿍 넣은 반죽에 해물을 넣어 노릇하게 부칩니다."),
                new Recipe("오징어볶음", List.of("오징어", "고추장", "양파", "당근"), "매콤한 양념에 오징어와 채소를 센불에 볶아 완성합니다."),
                new Recipe("카레라이스", List.of("카레가루", "감자", "당근", "양파", "밥"), "채소와 고기를 볶아 카레를 풀고 밥과 함께 제공합니다."),
                new Recipe("닭갈비", List.of("닭고기", "고추장", "양배추", "고구마"), "닭고기와 채소를 매콤한 양념으로 철판에 볶습니다."),
                new Recipe("갈비찜", List.of("소갈비", "무", "당근", "간장"), "갈비를 부드럽게 삶아 간장양념에 채소와 함께 조립니다."),
                new Recipe("삼계탕", List.of("닭고기", "인삼", "대추", "마늘"), "닭 안에 찹쌀을 넣고 한방 재료와 함께 푹 고아냅니다."),
                new Recipe("갈비탕", List.of("소갈비", "무", "대파"), "소갈비를 푹 고아 시원하고 깊은 국물을 냅니다."),
                new Recipe("치킨마요 덮밥", List.of("닭고기", "마요네즈", "밥", "간장"), "닭고기를 간장양념에 볶아 밥 위에 올리고 마요네즈를 뿌립니다."),
                new Recipe("멸치볶음", List.of("멸치", "간장", "설탕", "고추"), "멸치를 바삭하게 볶은 뒤 달짝지근하게 조립니다.")
        );

        // Determine main ingredients: take the first 2 extracted items as primary (if available)
        List<String> primary = ingredients.size() >= 2 ? ingredients.subList(0, 2) : new ArrayList<>(ingredients);
        Set<String> ingSet = new HashSet<>(ingredients);
        Set<String> primarySet = new HashSet<>(primary);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Recipe r : db) {
            int base = 0;
            int primaryBoost = 0;
            for (String need : r.need) {
                if (containsAny(ingSet, need)) {
                    base += 1; // each matching ingredient
                }
                if (containsAny(primarySet, need)) {
                    primaryBoost += 2; // prioritize recipes containing main ingredients
                }
            }
            int score = base + primaryBoost;
            if (score >= 1) { // at least slightly relevant
                Map<String, Object> m = new HashMap<>();
                m.put("name", r.name);
                m.put("need", r.need);
                m.put("desc", r.desc);
                m.put("matchCount", score); // keep key name for sorting compatibility
                out.add(m);
            }
        }
        // Sort by weighted score desc
        out.sort((a, b) -> Integer.compare((int) b.get("matchCount"), (int) a.get("matchCount")));
        return out;
    }

    private static boolean containsAny(Set<String> set, String token) {
        // token may be Korean baseline keyword; allow exact contains
        if (set.contains(token)) return true;
        // also check lower-cased variants
        String lt = token.toLowerCase();
        for (String s : set) if (s.equalsIgnoreCase(lt) || s.toLowerCase().contains(lt) || lt.contains(s.toLowerCase())) return true;
        return false;
    }

    private static List<String> dedupLowerPreserve(List<String> input) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String s : input) {
            map.putIfAbsent(s.toLowerCase(), s);
        }
        return new ArrayList<>(map.values());
    }

    private static class Recipe {
        final String name; final List<String> need; final String desc;
        Recipe(String name, List<String> need, String desc) { this.name = name; this.need = need; this.desc = desc; }
    }
}



package com.example.ocrproject.controller;

import com.example.ocrproject.service.GoogleOcrService;
import com.example.ocrproject.service.GeminiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}



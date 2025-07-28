package com.example.ocrproject.service;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class GoogleOcrService {

    public String extractText(MultipartFile file) {
        try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
            ByteString imgBytes = ByteString.copyFrom(file.getBytes());

            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request =
                    AnnotateImageRequest.newBuilder()
                            .addFeatures(feat)
                            .setImage(img)
                            .build();

            List<AnnotateImageResponse> responses = client.batchAnnotateImages(List.of(request)).getResponsesList();
            if (responses.isEmpty()) return "";

            return responses.get(0).getFullTextAnnotation().getText();
        } catch (Exception e) {
            throw new RuntimeException("Google OCR 호출 실패: " + e.getMessage(), e);
        }
    }
}
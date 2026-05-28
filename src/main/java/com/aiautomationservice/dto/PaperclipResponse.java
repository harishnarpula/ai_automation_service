package com.aiautomationservice.dto;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaperclipResponse {
    private String paperclipId;
    private String fileName;         // ← ADD
    private String s3FileUrl;        // ← ADD
    private String uploadedAt;
    private PaperclipAnalysisResult analysis;
}

package com.aiautomationservice.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaperclipRawExtraction {
    private RawSummary summary;
    private List<RawPerson> people;
    private List<RawCompany> companies;
    private List<RawReport> reports;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawSummary {
        private String shortSummary, detailedSummary;
        private List<String> keyPoints, actionItems;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawPerson { private String name, designation, company; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawCompany { private String name; }
    @Data
    public static class RawReport {

        private String title;
        private String source;
        private String downloadUrl;
    }
    public static PaperclipRawExtraction empty() {
        return PaperclipRawExtraction.builder()
                .summary(RawSummary.builder().shortSummary("Analysis unavailable.")
                        .detailedSummary("").keyPoints(List.of()).actionItems(List.of()).build())
                .people(List.of()).companies(List.of()).reports(List.of()).build();
    }
}

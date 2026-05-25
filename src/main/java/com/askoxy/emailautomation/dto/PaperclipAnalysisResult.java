package com.askoxy.emailautomation.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaperclipAnalysisResult {
    private Summary summary;
    private List<Person> people;
    private List<Company> companies;
    private List<Report> reports;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {
        private String shortSummary, detailedSummary;
        private List<String> keyPoints, actionItems;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Person { private String name, designation, company, linkedin; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Company { private String name, website, linkedin; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Report { private String title, source, downloadUrl; }
}

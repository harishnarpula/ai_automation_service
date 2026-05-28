package com.aiautomationservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormatDto {

    // ───────────────── REQUEST FIELDS ─────────────────

    private String entityId;

    // VIDEO | CONTENT | PAPERCLIP
    private String entityType;

    // Example: [LINKEDIN, INSTAGRAM, TWITTER]
    private List<String> platforms;

    // Optional edited/custom content from frontend
    private String editedContent;

    // ───────────────── RESPONSE FIELDS ─────────────────

    // Returned by server only
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String contentId;

    // IMPORTANT:
    // DO NOT make READ_ONLY
    // because frontend sends this same payload to /social/publish
    private Map<String, PlatformContent> formattedContent;

    // Optional backward compatibility field
    private Map<String, PlatformContent> formats;
}
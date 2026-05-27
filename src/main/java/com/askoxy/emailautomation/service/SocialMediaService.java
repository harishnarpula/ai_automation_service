package com.askoxy.emailautomation.service;

import com.askoxy.emailautomation.dto.PlatformContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialMediaService {

    private final AIService aiService;

    @Value("${radha.n8n.webhook-base-url:http://localhost:5678/webhook-test}")
    private String n8nWebhookBaseUrl;

    @Value("${radha.n8n.enabled:false}")
    private boolean n8nEnabled;

    // ─────────────────────────────────────────────────────────────
    // FORMAT CONTENT FOR PLATFORMS
    // ─────────────────────────────────────────────────────────────

    public Map<String, PlatformContent> formatForPlatforms(
            String content,
            List<String> platforms,
            String imageUrl,
            String videoUrl
    ) {

        Map<String, PlatformContent> results = new LinkedHashMap<>();

        for (String platform : platforms) {

            try {

                String upperPlatform = platform.toUpperCase();

                String formattedText = aiService.chat(
                        "You are Radha's social media manager. Return ONLY the formatted content.",
                        buildPrompt(content, upperPlatform)
                );

                String hashtags = extractHashtags(formattedText);
                String cleanText = removeHashtags(formattedText);
                String limitLabel = getLimitLabel(upperPlatform);

                PlatformContent pc = PlatformContent.builder()
                        .platform(upperPlatform)
                        .text(cleanText)
                        .hashtags(hashtags)
                        .title(generateTitle(content))
                        .subject(generateSubject(content))
                        .imageUrl(imageUrl)
                        .videoUrl(videoUrl)
                        .charCount(cleanText.length())
                        .limit(limitLabel)
                        .build();

                results.put(upperPlatform, pc);

            } catch (Exception ex) {

                log.error("Formatting failed for {} : {}", platform, ex.getMessage());

                results.put(platform.toUpperCase(),
                        PlatformContent.builder()
                                .platform(platform.toUpperCase())
                                .text("FORMATTING_FAILED")
                                .hashtags("")
                                .charCount(0)
                                .limit("N/A")
                                .build());
            }
        }

        return results;
    }

    // ─────────────────────────────────────────────────────────────
    // BUILD PLATFORM PROMPT
    // ─────────────────────────────────────────────────────────────

    private String buildPrompt(String content, String platform) {

        return switch (platform) {

            case "LINKEDIN" -> """
                    Platform: LinkedIn
                    - Professional warm tone
                    - Strong hook first line
                    - Storytelling style
                    - Readable paragraphs
                    - End with engagement question
                    - Add hashtags at end
                    Content:
                    """ + content;

            case "INSTAGRAM" -> """
                    Platform: Instagram
                    - Casual energetic tone
                    - Use emojis naturally
                    - Add CTA
                    - Add hashtags at end
                    Content:
                    """ + content;

            case "FACEBOOK" -> """
                    Platform: Facebook
                    - Conversational human tone
                    - Emotional storytelling style
                    - Hook first 2 lines
                    - Add emojis naturally
                    - End with engagement question
                    - Add hashtags at end
                    Content:
                    """ + content;

            case "TWITTER" -> """
                    Platform: Twitter/X
                    - STRICT 280 characters
                    - Punchy concise style
                    - 1-2 hashtags maximum
                    Content:
                    """ + content;

            case "WHATSAPP" -> """
                    Platform: WhatsApp
                    - Personal conversational style
                    - Short and clear
                    - No hashtags
                    - Clear CTA
                    Content:
                    """ + content;

            default -> """
                    Adapt this content professionally:
                    """ + content;
        };
    }

    // ─────────────────────────────────────────────────────────────
    // PUBLISH TO ALL PLATFORMS
    // ─────────────────────────────────────────────────────────────

    public Map<String, String> postToAllPlatforms(
            Map<String, PlatformContent> formattedContent
    ) {

        Map<String, String> results = new LinkedHashMap<>();

        // NULL SAFETY
        if (formattedContent == null || formattedContent.isEmpty()) {

            results.put("ERROR", "No formatted content provided");

            return results;
        }

        for (Map.Entry<String, PlatformContent> entry
                : formattedContent.entrySet()) {

            try {

                PlatformContent pc = entry.getValue();

                if (pc == null) {

                    results.put(entry.getKey(),
                            "FAILED: Platform content is null");

                    continue;
                }

                results.put(entry.getKey(), fireN8n(pc));

            } catch (Exception ex) {

                log.error("Publish failed for {} : {}",
                        entry.getKey(),
                        ex.getMessage());

                results.put(entry.getKey(),
                        "FAILED: " + ex.getMessage());
            }
        }

        return results;
    }

    // ─────────────────────────────────────────────────────────────
    // SEND TO N8N WEBHOOK
    // ─────────────────────────────────────────────────────────────

    private String fireN8n(PlatformContent pc) {

        if (!n8nEnabled) {

            log.info("n8n disabled — skipping publish for {}",
                    pc.getPlatform());

            return "N8N_DISABLED";
        }

        try {

            RestTemplate rt = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("platform", pc.getPlatform());
            payload.put("title", pc.getTitle());
            payload.put("subject", pc.getSubject());
            payload.put("text", pc.getText());
            payload.put("hashtags", pc.getHashtags());
            payload.put("imageUrl", pc.getImageUrl());
            payload.put("videoUrl", pc.getVideoUrl());
            payload.put("paperclipUrl", pc.getPaperclipUrl());
            payload.put("charCount", pc.getCharCount());
            payload.put("limit", pc.getLimit());

            ResponseEntity<String> response = rt.postForEntity(
                    n8nWebhookBaseUrl + "/social",
                    new HttpEntity<>(payload, headers),
                    String.class
            );

            return response.getStatusCode().is2xxSuccessful()
                    ? "N8N_TRIGGERED"
                    : "N8N_ERROR";

        } catch (Exception ex) {

            log.error("n8n failed for {} : {}",
                    pc.getPlatform(),
                    ex.getMessage());

            return "N8N_FAILED: " + ex.getMessage();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private String generateTitle(String content) {

        try {

            return aiService.chat(
                    "Generate short catchy title only.",
                    content
            );

        } catch (Exception ex) {

            return "Generated Content";
        }
    }

    private String generateSubject(String content) {

        try {

            return aiService.chat(
                    "Generate short email/newsletter subject only.",
                    content
            );

        } catch (Exception ex) {

            return "New Update";
        }
    }

    private String extractHashtags(String text) {

        StringBuilder sb = new StringBuilder();

        for (String word : text.split("\\s+")) {

            if (word.startsWith("#")) {

                sb.append(word).append(" ");
            }
        }

        return sb.toString().trim();
    }

    private String removeHashtags(String text) {

        return text.replaceAll("#\\S+", "").trim();
    }

    private String getLimitLabel(String platform) {

        return switch (platform) {

            case "TWITTER" -> "280 chars max";
            case "LINKEDIN" -> "3000 chars max";
            case "INSTAGRAM" -> "2200 chars max";
            case "FACEBOOK" -> "63206 chars max";
            case "WHATSAPP" -> "4096 chars max";
            default -> "Platform limit";
        };
    }

    private String encode(String value) {

        try {

            return URLEncoder.encode(
                    value,
                    StandardCharsets.UTF_8
            );

        } catch (Exception ex) {

            return value;
        }
    }
}
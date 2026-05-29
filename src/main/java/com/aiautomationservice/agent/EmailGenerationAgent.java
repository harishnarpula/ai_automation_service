package com.aiautomationservice.agent;

import com.aiautomationservice.dto.GeneratedEmailDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailGenerationAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are writing a B2B outreach email on behalf of OUR company to a potential client.

            The goal of this email — as directed by our CEO — is to:
            1. Introduce our company briefly and specifically
            2. Showcase what we have built and what we can build for the client
            3. Make the client want to hire us or explore working with us
            4. Get a reply or a short call booked

            EMAIL STRUCTURE — follow this exactly:
            Hi [ClientFirstName],

            [Line 1-2: Who we are and what we do — specific, not generic]

            [Line 3-4: What we have built / what we can build or deliver for them]

            [Line 5: Why working with us makes sense — one concrete reason]

            [Line 6: Single CTA — low friction, 15-min call or quick question]

            Warm regards,
            OXYGLOBAL.TECH Team
            sales@oxyglobaltech.xyz

            STRICT RULES:
            - Max 150 words in body
            - Use "we have", "we build", "we deliver" — not "I" statements
            - Be specific — pull real facts from the company context
            - NEVER use: innovative, cutting-edge, seamlessly, leverage, synergy, world-class
            - NEVER use generic openers like "I hope this finds you well"
            - NEVER write the sign-off inline with the last sentence — it must be on its own lines
            - Subject line: specific, under 8 words, makes them want to open it
            - Sound like a real founder, not a marketing template
            - Each paragraph must be separated by a blank line (\\n\\n)

            OUTPUT FORMAT — CRITICAL:
            - Your ENTIRE response must be a single JSON object
            - No preamble, no explanation, no markdown fences
            - Start your response with { and end with }

            REQUIRED JSON FORMAT:
            {"subject": "subject here", "body": "email body here"}
            """;

    private static final String BULK_SYSTEM_PROMPT = """
            You are writing a B2B outreach email on behalf of OUR company to multiple potential clients.

            The goal of this email — as directed by our CEO — is to:
            1. Introduce our company briefly and specifically
            2. Showcase what we have built and what we can build for the client
            3. Make the client want to hire us or explore working with us
            4. Get a reply or a short call booked

            EMAIL STRUCTURE — follow this exactly:
            Hi {clientName},

            [Line 1-2: Who we are and what we do — specific, not generic]

            [Line 3-4: What we have built / what we can build or deliver for them]

            [Line 5: Why working with us makes sense — one concrete reason]

            [Line 6: Single CTA — low friction, 15-min call or quick question]

            Warm regards,
            OXYGLOBAL.TECH Team
            sales@oxyglobaltech.xyz

            PLACEHOLDER RULES — CRITICAL:
            - You MUST use {clientName} wherever the client's first name appears
            - You MUST use {clientCompany} wherever the client's company name appears
            - These will be replaced with real values before sending — do NOT use actual names

            STRICT RULES:
            - Max 150 words in body
            - Use "we have", "we build", "we deliver" — not "I" statements
            - Be specific — pull real facts from the company context
            - NEVER use: innovative, cutting-edge, seamlessly, leverage, synergy, world-class
            - NEVER use generic openers like "I hope this finds you well"
            - NEVER write the sign-off inline with the last sentence — it must be on its own lines
            - Each paragraph must be separated by a blank line (\\n\\n)
            - Sound like a real founder, not a marketing template

            OUTPUT FORMAT — CRITICAL:
            - Your ENTIRE response must be a single JSON object
            - No preamble, no explanation, no markdown fences
            - Start your response with { and end with }

            REQUIRED JSON FORMAT:
            {"subject": "subject here", "body": "email body here"}
            """;

    public GeneratedEmailDto generateEmail(String clientName, String companyIntelligence,
                                           String strategy, String retrievedContext) {
        return generateEmail(clientName, companyIntelligence, strategy, retrievedContext, null);
    }

    public GeneratedEmailDto generateEmail(String clientName, String companyIntelligence,
                                           String strategy, String retrievedContext,
                                           String feedbackHistory) {
        boolean hasFeedback = feedbackHistory != null && !feedbackHistory.isBlank();

        String systemPrompt = hasFeedback
                ? SYSTEM_PROMPT + "\nREVISION INSTRUCTIONS (admin rejected previous version):\n"
                  + feedbackHistory
                  + "\nIncorporate ALL feedback silently. Do NOT acknowledge it. Just write the improved email as JSON."
                : SYSTEM_PROMPT;

        String userPrompt = "CLIENT NAME: " + clientName + "\n\n"
                + "OUR COMPANY INTELLIGENCE:\n" + companyIntelligence + "\n\n"
                + "EMAIL STRATEGY:\n" + strategy + "\n\n"
                + "RAW COMPANY CONTEXT (use specific facts from here):\n" + retrievedContext + "\n\n"
                + "Write the email now. Each paragraph separated by blank lines. Sign-off on its own line.";

        log.info("[EmailGenAgent] Generating single email for client={} hasFeedback={}", clientName, hasFeedback);

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        log.debug("[EmailGenAgent] Raw response: {}", response);
        return parseResponse(response, clientName);
    }

    public GeneratedEmailDto generateBulkEmail(String previewClientName,
                                               String previewClientCompany,
                                               String intelligence,
                                               String strategy,
                                               String context) {
        String userPrompt = "OUR COMPANY INTELLIGENCE:\n" + intelligence + "\n\n"
                + "EMAIL STRATEGY:\n" + strategy + "\n\n"
                + "RAW COMPANY CONTEXT (use specific facts from here):\n" + context + "\n\n"
                + "REMINDER: Use {clientName} and {clientCompany} as placeholders — never real names.\n\n"
                + "Write the bulk campaign email template now as a JSON object.";

        log.info("[EmailGenAgent] Generating bulk email template. previewClient={} company={}",
                previewClientName, previewClientCompany);

        String response = chatClient.prompt()
                .system(BULK_SYSTEM_PROMPT)
                .user(userPrompt)
                .call()
                .content();

        log.debug("[EmailGenAgent] Bulk raw response: {}", response);

        GeneratedEmailDto result = parseResponse(response, previewClientName);

        if (!result.getBody().contains("{clientName}")) {
            log.warn("[EmailGenAgent] AI did not use {{clientName}} placeholder — forcing it in greeting.");
            String fixedBody = "Hi {clientName},\n\n" + result.getBody();
            result = GeneratedEmailDto.builder()
                    .subject(result.getSubject())
                    .body(fixedBody)
                    .build();
        }

        return result;
    }

    private GeneratedEmailDto parseResponse(String response, String clientName) {
        String cleaned = response
                .replaceAll("(?i)```json", "")
                .replaceAll("```", "")
                .trim();

        try {
            JsonNode node = objectMapper.readTree(cleaned);
            if (node.has("subject") && node.has("body")) {
                return GeneratedEmailDto.builder()
                        .subject(node.get("subject").asText())
                        .body(node.get("body").asText())
                        .build();
            }
        } catch (Exception ignored) {}

        try {
            Matcher matcher = Pattern.compile("\\{[^{}]*\"subject\"[^{}]*\"body\"[^{}]*\\}",
                    Pattern.DOTALL).matcher(cleaned);
            if (matcher.find()) {
                JsonNode node = objectMapper.readTree(matcher.group());
                log.warn("[EmailGenAgent] Extracted JSON from prose for client={}", clientName);
                return GeneratedEmailDto.builder()
                        .subject(node.get("subject").asText())
                        .body(node.get("body").asText())
                        .build();
            }
        } catch (Exception ignored) {}

        log.error("[EmailGenAgent] Could not parse JSON for client={}. Raw: {}", clientName, response);
        return GeneratedEmailDto.builder()
                .subject("Introduction from our team — " + clientName)
                .body(response.trim())
                .build();
    }
}
package com.aiautomationservice.service;

// ─────────────────────────────────────────────────────────────────────────────
// NEW FILE — WhatsApp Lead Flow
// Handles all WhatsApp API calls via UltraMsg REST API.
// Used by LeadService (outreach) and WebhookService (replies).
// ─────────────────────────────────────────────────────────────────────────────

import com.aiautomationservice.config.UltraMsgProperties;
import com.aiautomationservice.exception.UltraMsgException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class UltraMsgService {

    private static final Logger log = LoggerFactory.getLogger(UltraMsgService.class);

    /** UltraMsg expects form-encoded body for /messages/chat */
    private static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded");

    private final OkHttpClient httpClient;
    private final UltraMsgProperties props;
    private final ObjectMapper objectMapper;

    public UltraMsgService(OkHttpClient httpClient,
                           UltraMsgProperties props,
                           ObjectMapper objectMapper) {
        this.httpClient   = httpClient;
        this.props        = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a WhatsApp message to an individual lead.
     *
     * @param phone   lead's phone in international format (e.g. 919876543210)
     * @param message text to send
     */
    public void sendMessageToLead(String phone, String message) {
        String recipient = normalizePhone(phone);
        log.info("[UltraMsg] Sending message to lead: {}", recipient);
        sendMessage(recipient, message);
    }

    /**
     * Sends a WhatsApp alert to the team group.
     *
     * @param message text to send
     */
    public void sendMessageToTeam(String message) {
        String groupId = props.getTeamGroupId();
        log.info("[UltraMsg] Sending alert to team group: {}", groupId);
        sendMessage(groupId, message);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Core send method — POSTs to UltraMsg /messages/chat endpoint.
     * Throws UltraMsgException on HTTP error, null body, or network failure.
     *
     * FIX: replaced Objects.requireNonNull(response.body()) — that throws a bare
     * NullPointerException with no context. Now we check explicitly and raise a
     * descriptive UltraMsgException so callers know exactly what happened.
     */
    private void sendMessage(String to, String message) {
        String baseUrl = props.getBaseUrl().endsWith("/") ? props.getBaseUrl() : props.getBaseUrl() + "/";
        String url = baseUrl + "messages/chat";

        RequestBody body = new FormBody.Builder()
                .add("token", props.getToken())
                .add("to", to)
                .add("body", message)
                .add("priority", "10")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            // ── FIX: null-safe body read ────────────────────────────────────
            ResponseBody rawBody = response.body();
            if (rawBody == null) {
                throw new UltraMsgException(
                        "UltraMsg returned HTTP " + response.code() + " with an empty (null) response body",
                        response.code()
                );
            }
            String responseBody = rawBody.string();
            // ───────────────────────────────────────────────────────────────

            log.info("[UltraMsg] Response to={} code={} body={}", to, response.code(), responseBody);

            if (!response.isSuccessful()) {
                throw new UltraMsgException(
                        "UltraMsg returned HTTP " + response.code() + ": " + responseBody,
                        response.code()
                );
            }

            // UltraMsg returns {"sent":"true"} on success; {"error":"..."} on failure
            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("error")) {
                throw new UltraMsgException(
                        "UltraMsg error: " + json.get("error").asText(),
                        response.code()
                );
            }

            log.info("[UltraMsg] Message delivered to: {}", to);

        } catch (IOException e) {
            throw new UltraMsgException("Failed to reach UltraMsg API: " + e.getMessage(), e);
        }
    }

    /**
     * Normalises a phone number to UltraMsg's expected format.
     * Groups already carry @g.us — leave them untouched.
     * Any plain number gets digits-only + @c.us appended.
     * e.g. "+91 98765-43210" → "919876543210@c.us"
     */
    private String normalizePhone(String phone) {
        if (phone != null && phone.contains("@")) return phone;
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        return digits + "@c.us";
    }
}
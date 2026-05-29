package com.aiautomationservice.controller;

import com.aiautomationservice.dto.ApiResponse;
import com.aiautomationservice.dto.WebhookPayload;
import com.aiautomationservice.service.WebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/webhook")
public class WhatsAppLeadWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public WhatsAppLeadWebhookController(WebhookService webhookService,
                                     ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.objectMapper   = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ROOT CAUSE FIX:
    //
    // UltraMsg sends webhook callbacks as application/x-www-form-urlencoded
    // (form data), NOT as application/json. The old @RequestBody annotation
    // only accepts JSON — Spring returned HTTP 400 silently for every real
    // UltraMsg webhook call. UltraMsg retried 3 times then gave up.
    // That's why curl worked (we sent JSON manually) but real WhatsApp replies
    // never triggered a response.
    //
    // Fix: Accept BOTH form data AND JSON using two mapped methods.
    // UltraMsg sends the entire payload as a JSON string inside a form field
    // called "data". We parse that JSON string into our WebhookPayload DTO.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Handles UltraMsg real webhook — sent as form data.
     * UltraMsg POSTs: data={"event_type":"message_received","data":{...}}
     */
    @PostMapping(
            value = "/whatsapp",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public ResponseEntity<ApiResponse<Void>> receiveWhatsAppForm(
            @RequestParam Map<String, String> formParams) {

        log.info("[Webhook] Form POST received — params: {}", formParams.keySet());

        try {
            // UltraMsg puts the full JSON payload in a field named "data"
            String jsonData = formParams.get("data");

            if (jsonData == null || jsonData.isBlank()) {
                // Some UltraMsg versions send fields directly (flat form)
                // Build a minimal JSON from flat form fields
                jsonData = buildJsonFromFlatForm(formParams);
            }

            log.info("[Webhook] Parsed UltraMsg payload: {}", jsonData);
            WebhookPayload payload = objectMapper.readValue(jsonData, WebhookPayload.class);
            webhookService.processWebhook(payload);

        } catch (Exception e) {
            log.error("[Webhook] Failed to parse form payload: {} | raw: {}", e.getMessage(), formParams);
            // Return 200 anyway so UltraMsg doesn't retry endlessly
        }

        return ResponseEntity.ok(ApiResponse.ok("Webhook processed"));
    }

    /**
     * Handles curl/test calls sent as JSON — kept for manual testing.
     */
    @PostMapping(
            value = "/whatsapp",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ApiResponse<Void>> receiveWhatsAppJson(
            @RequestBody WebhookPayload payload) {

        log.info("[Webhook] JSON POST received: eventType={}, from={}",
                payload.getEventType(),
                payload.getData() != null ? payload.getData().getFrom() : "null");

        webhookService.processWebhook(payload);
        return ResponseEntity.ok(ApiResponse.ok("Webhook processed"));
    }

    /**
     * GET /webhook/ping — health check
     */
    @GetMapping("/ping")
    public ResponseEntity<ApiResponse<String>> ping() {
        log.info("[Webhook] Ping received — endpoint is alive");
        return ResponseEntity.ok(ApiResponse.ok("Webhook is alive", "pong"));
    }

    /**
     * POST /webhook/test — manual simulation without WhatsApp
     * Usage: POST /webhook/test?phone=916281565528&message=Canada
     */
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> testWebhook(
            @RequestParam String phone,
            @RequestParam String message) {

        log.info("[Webhook] Manual test — phone={}, message={}", phone, message);

        WebhookPayload payload     = new WebhookPayload();
        WebhookPayload.MessageData data = new WebhookPayload.MessageData();
        data.setFrom(phone + "@c.us");
        data.setBody(message);
        data.setType("chat");
        data.setFromMe(false);
        payload.setEventType("message_received");
        payload.setData(data);

        webhookService.processWebhook(payload);
        return ResponseEntity.ok(ApiResponse.ok("Test webhook fired for " + phone));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Builds a JSON string from flat UltraMsg form fields.
     * UltraMsg may send fields like: from=919..@c.us, body=Canada, type=chat
     */
    private String buildJsonFromFlatForm(Map<String, String> p) {
        String from    = p.getOrDefault("from",      "");
        String body    = p.getOrDefault("body",      "");
        String type    = p.getOrDefault("type",      "chat");
        String fromMe  = p.getOrDefault("fromMe",    "false");
        String eventType = p.getOrDefault("event_type", "message_received");

        return String.format(
                "{\"event_type\":\"%s\",\"data\":{\"from\":\"%s\",\"body\":\"%s\",\"type\":\"%s\",\"fromMe\":%s}}",
                eventType, from, body, type, fromMe
        );
    }
}
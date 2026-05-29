package com.aiautomationservice.service;


import com.aiautomationservice.dto.WebhookPayload;
import com.aiautomationservice.repository.ProcessedLeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookAsyncHandler     asyncHandler;
    private final ProcessedLeadRepository processedLeadRepo;

    public WebhookService(WebhookAsyncHandler asyncHandler,
                          ProcessedLeadRepository processedLeadRepo) {
        this.asyncHandler      = asyncHandler;
        this.processedLeadRepo = processedLeadRepo;
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public void processWebhook(WebhookPayload payload) {
        log.info("[WebhookService] Webhook received: eventType={}", payload.getEventType());

        if (payload.getData() == null) {
            log.warn("[WebhookService] Null data — skipping");
            return;
        }

        WebhookPayload.MessageData data = payload.getData();

        if (data.isFromMe()) {
            log.debug("[WebhookService] Skipping outbound (fromMe=true)");
            return;
        }

        if (!"chat".equalsIgnoreCase(data.getType()) &&
                !"text".equalsIgnoreCase(data.getType())) {
            log.debug("[WebhookService] Skipping non-text type: {}", data.getType());
            return;
        }

        String phone     = cleanPhone(data.getPhoneNumber());
        String leadReply = data.getBody();

        // ── LEAD NUMBER CHECK ────────────────────────────────────────────────
        // Only process messages from phones registered as leads.
        // Anyone else (team members, random contacts) is silently ignored.
        if (!processedLeadRepo.existsByPhone(phone)) {
            log.warn("[WebhookService] Ignored message from non-lead phone: {}", phone);
            return;
        }
        // ────────────────────────────────────────────────────────────────────

        log.info("[WebhookService] Lead reply from: {} | message: {}", phone, leadReply);

        asyncHandler.handleLeadReplyAsync(phone, leadReply);

        log.info("[WebhookService] Async handler triggered for: {}", phone);
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    /** Always store/query plain digits — no @c.us suffix */
    private String cleanPhone(String phone) {
        if (phone == null) return "";
        return phone.replace("@c.us", "").replace("@g.us", "").replaceAll("[^0-9]", "");
    }
}
package com.aiautomationservice.controller;

import com.aiautomationservice.dto.EmailAutomationDto;
import com.aiautomationservice.service.EmailAutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Triggers the bulk campaign AI pipeline + WhatsApp approval for a given campaignId.
 *
 * Flow:
 *   POST /api/v1/campaign/trigger?campaignId=campaign-abc123
 *     → loads all PENDING clients from DB
 *     → runs AI agent pipeline (generates template with {clientName}/{clientCompany})
 *     → sends WhatsApp preview to admin
 *     → waits for admin APPROVE
 *     → ApprovalOrchestrationService loops and sends to all clients
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/campaign")
@RequiredArgsConstructor
public class BulkCampaignController {

    private final EmailAutomationService emailAutomationService;

    @PostMapping("/trigger")
    public ResponseEntity<EmailAutomationDto> triggerBulkCampaign(
            @RequestParam("campaignId") String campaignId
    ) {
        log.info("[BulkCampaignController] Trigger request for campaignId={}", campaignId);

        if (campaignId == null || campaignId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    EmailAutomationDto.builder()
                            .success(false)
                            .message("campaignId is required.")
                            .build()
            );
        }

        try {
            EmailAutomationDto response = emailAutomationService.startBulkCampaign(campaignId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[BulkCampaignController] Failed to trigger campaign={}", campaignId, e);
            return ResponseEntity.internalServerError().body(
                    EmailAutomationDto.builder()
                            .success(false)
                            .message("Failed to start campaign: " + e.getMessage())
                            .build()
            );
        }
    }
}
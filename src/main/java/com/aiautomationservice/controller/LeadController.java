package com.aiautomationservice.controller;

// ─────────────────────────────────────────────────────────────────────────────
// NEW FILE — WhatsApp Lead Flow
// Exposes POST /lead — called by Google Apps Script when a new row is added
// to Google Sheets. Validates the payload and triggers WhatsApp outreach.
// ─────────────────────────────────────────────────────────────────────────────

import com.aiautomationservice.dto.ApiResponse;
import com.aiautomationservice.dto.LeadRequest;
import com.aiautomationservice.service.LeadService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lead")
public class LeadController {

    private static final Logger log = LoggerFactory.getLogger(LeadController.class);

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    /**
     * POST /lead
     * Receives a new lead from Google Apps Script.
     * Validates required fields (name, phone) then sends WhatsApp outreach.
     *
     * @param lead JSON body mapped from Google Sheets row
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> receiveLead(@Valid @RequestBody LeadRequest lead) {
        log.info("[LeadController] New lead received: row={}, name={}", lead.getRowNumber(), lead.getName());
        leadService.processLead(lead);
        return ResponseEntity.ok(
                ApiResponse.ok("Lead received — WhatsApp message sent to " + lead.getPhone())
        );
    }

    /**
     * GET /lead/ping
     * Quick connectivity check — useful for testing from Apps Script before going live.
     */
    @GetMapping("/ping")
    public ResponseEntity<ApiResponse<String>> ping() {
        return ResponseEntity.ok(ApiResponse.ok("Lead endpoint is alive", "pong"));
    }
}
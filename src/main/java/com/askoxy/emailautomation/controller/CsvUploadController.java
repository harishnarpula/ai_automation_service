package com.askoxy.emailautomation.controller;

import com.askoxy.emailautomation.config.MultiSenderConfig;
import com.askoxy.emailautomation.entity.CampaignClient;
import com.askoxy.emailautomation.repository.CampaignClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/**
 * REST controller for uploading the campaign client CSV.
 *
 * POST /api/v1/campaign/upload-csv
 *   → Parses CSV
 *   → Assigns round-robin senders
 *   → Saves all CampaignClient rows to DB
 *   → Returns campaignId + sender distribution summary
 *
 * Expected CSV format (with header row):
 *   client_name,client_company,client_email
 *   John Doe,Acme Corp,john@acme.com
 *   Jane Smith,TechHub,jane@techhub.com
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/campaign")
@RequiredArgsConstructor
public class CsvUploadController {

    private final CampaignClientRepository campaignClientRepository;
    private final MultiSenderConfig multiSenderConfig;

    // ── Upload Endpoint ───────────────────────────────────────────────────────

    @PostMapping("/upload-csv")
    public ResponseEntity<Map<String, Object>> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "campaignId", required = false) String campaignId
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No file uploaded or file is empty."));
        }

        // Auto-generate campaignId if not provided
        if (campaignId == null || campaignId.isBlank()) {
            campaignId = "campaign-" + UUID.randomUUID().toString().substring(0, 8);
        }

        log.info("[CsvUpload] Processing upload for campaignId={} file={}", campaignId, file.getOriginalFilename());

        List<CampaignClient> clients = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> senderEmails = multiSenderConfig.getSenderEmails();

        if (senderEmails.isEmpty()) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No sender emails configured in application.properties."));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {

            String line;
            int lineNumber = 0;
            boolean headerSkipped = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip blank lines
                if (line.isBlank()) continue;

                // Skip header row (first non-blank line)
                if (!headerSkipped) {
                    headerSkipped = true;
                    log.debug("[CsvUpload] Skipping header: {}", line);
                    continue;
                }

                // Parse CSV row
                String[] parts = line.split(",", -1);
                if (parts.length < 3) {
                    errors.add("Line " + lineNumber + ": expected 3 columns, got " + parts.length);
                    continue;
                }

                String clientName    = parts[0].trim();
                String clientCompany = parts[1].trim();
                String clientEmail   = parts[2].trim().toLowerCase();

                // Basic validation
                if (clientName.isBlank() || clientCompany.isBlank() || clientEmail.isBlank()) {
                    errors.add("Line " + lineNumber + ": blank field(s) — skipping.");
                    continue;
                }

                if (!clientEmail.contains("@")) {
                    errors.add("Line " + lineNumber + ": invalid email '" + clientEmail + "' — skipping.");
                    continue;
                }

                // ── Round-robin sender assignment ──────────────────────────────
                // clients.size() gives 0-based index of the client being added
                String assignedSender = senderEmails.get(clients.size() % senderEmails.size());

                CampaignClient client = CampaignClient.builder()
                        .clientName(clientName)
                        .clientCompany(clientCompany)
                        .clientEmail(clientEmail)
                        .campaignId(campaignId)
                        .assignedSender(assignedSender)
                        .status("PENDING")
                        .build();

                clients.add(client);
                log.debug("[CsvUpload] Parsed client: {} → sender: {}", clientEmail, assignedSender);
            }

        } catch (Exception e) {
            log.error("[CsvUpload] Failed to parse CSV", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to parse CSV: " + e.getMessage()));
        }

        if (clients.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "No valid clients found in CSV.",
                            "parseErrors", errors
                    ));
        }

        // Save all clients to DB
        campaignClientRepository.saveAll(clients);
        log.info("[CsvUpload] Saved {} clients for campaignId={}", clients.size(), campaignId);

        // Build sender distribution summary
        Map<String, Long> senderDistribution = new LinkedHashMap<>();
        for (CampaignClient c : clients) {
            senderDistribution.merge(c.getAssignedSender(), 1L, Long::sum);
        }

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("campaignId", campaignId);
        response.put("totalClients", clients.size());
        response.put("senderDistribution", senderDistribution);
        if (!errors.isEmpty()) {
            response.put("parseWarnings", errors);
        }

        log.info("[CsvUpload] Upload complete. campaignId={} totalClients={} senderDist={}",
                campaignId, clients.size(), senderDistribution);

        return ResponseEntity.ok(response);
    }

    // ── Status Check Endpoint ─────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCampaignStatus(
            @RequestParam("campaignId") String campaignId
    ) {
        long total   = campaignClientRepository.countByCampaignId(campaignId);
        long sent    = campaignClientRepository.countByCampaignIdAndStatus(campaignId, "SENT");
        long failed  = campaignClientRepository.countByCampaignIdAndStatus(campaignId, "FAILED");
        long pending = campaignClientRepository.countByCampaignIdAndStatus(campaignId, "PENDING");

        return ResponseEntity.ok(Map.of(
                "campaignId", campaignId,
                "total",      total,
                "sent",       sent,
                "failed",     failed,
                "pending",    pending
        ));
    }
}
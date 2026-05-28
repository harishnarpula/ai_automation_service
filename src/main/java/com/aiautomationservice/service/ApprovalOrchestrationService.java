package com.aiautomationservice.service;

import com.aiautomationservice.config.MultiSenderConfig;
import com.aiautomationservice.dto.GeneratedEmailDto;
import com.aiautomationservice.entity.CampaignClient;
import com.aiautomationservice.entity.EmailApprovalSession;
import com.aiautomationservice.repository.CampaignClientRepository;
import com.aiautomationservice.repository.EmailApprovalSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalOrchestrationService {

    private static final String APPROVE_KEYWORD = "APPROVE";

    private final EmailApprovalSessionRepository sessionRepository;
    private final CampaignClientRepository campaignClientRepository;
    private final RegenerationService regenerationService;
    private final EmailDeliveryService emailDeliveryService;
    private final WhatsAppNotificationService whatsAppNotificationService;
    private final MultiSenderConfig multiSenderConfig;

    @Transactional
    public void processAdminReply(String replyText) {
        String normalizedReply = normalizeReply(replyText);

        EmailApprovalSession session = sessionRepository
                .findTopByStatusOrderByCreatedAtDesc("PENDING_APPROVAL")
                .orElse(null);

        if (session == null) {
            log.warn("[Approval] Admin replied '{}' but NO PENDING_APPROVAL session exists. Ignoring.", normalizedReply);
            return;
        }

        if (isTerminalStatus(session.getStatus())) {
            log.warn("[Approval] Session {} is already terminal={}. Ignoring duplicate reply.",
                    session.getSessionId(), session.getStatus());
            return;
        }

        log.info("[Approval] Processing admin reply for sessionId={} sessionType={} status={}",
                session.getSessionId(), session.getSessionType(), session.getStatus());
        log.info("[Approval] Admin rawReply='{}' normalized='{}'", replyText, normalizedReply);

        if (isApproveCommand(normalizedReply)) {
            if ("BULK_CAMPAIGN".equals(session.getSessionType())) {
                handleBulkApproval(session);
            } else {
                handleApproval(session);
            }
        } else {
            handleRejection(session, normalizedReply);
        }
    }

    private void handleApproval(EmailApprovalSession session) {
        log.info("[Approval] APPROVED sessionId={} sessionType={} attempt={}",
                session.getSessionId(), session.getSessionType(), session.getAttemptCount());

        session.setStatus("APPROVED");
        sessionRepository.save(session);

        GeneratedEmailDto emailDto = GeneratedEmailDto.builder()
                .subject(session.getCurrentSubject())
                .body(session.getCurrentBody())
                .build();

        try {
            String inReplyTo = null;
            String references = null;
            if ("CLIENT_REPLY".equals(session.getSessionType())) {
                inReplyTo = session.getEmailThreadId();
                references = session.getEmailThreadId();
            }

            String sentMessageId = sendSingleWithFailover(session, emailDto, inReplyTo, references);
            session.setSentMessageId(sentMessageId);
            if (session.getEmailThreadId() == null || session.getEmailThreadId().isBlank()) {
                session.setEmailThreadId(sentMessageId);
            }
            sessionRepository.save(session);

            whatsAppNotificationService.sendDeliveryConfirmation(session);
            log.info("[Approval] Email delivered to client={}", session.getClientEmail());
        } catch (Exception e) {
            log.error("[Approval] Delivery failed for session={}, reverting to PENDING_APPROVAL",
                    session.getSessionId(), e);
            session.setStatus("PENDING_APPROVAL");
            sessionRepository.save(session);
            whatsAppNotificationService.sendDeliveryFailureAlert(session, e.getMessage());
        }
    }

    private void handleBulkApproval(EmailApprovalSession session) {
        log.info("[BulkApproval] APPROVED sessionId={} campaignId={}",
                session.getSessionId(), session.getEmailThreadId());

        session.setStatus("APPROVED");
        sessionRepository.save(session);

        String campaignId = session.getEmailThreadId();
        if (campaignId == null || campaignId.isBlank()) {
            log.error("[BulkApproval] Missing campaignId on sessionId={}. Marking as REGENERATION_FAILED.",
                    session.getSessionId());
            session.setStatus("REGENERATION_FAILED");
            sessionRepository.save(session);
            whatsAppNotificationService.sendRegenerationFailureAlert(
                    session, "Missing campaignId for bulk campaign session");
            return;
        }

        String subjectTemplate = session.getCurrentSubject();
        String bodyTemplate = session.getCurrentBody();

        List<CampaignClient> pendingClients = campaignClientRepository.findByCampaignIdAndStatus(campaignId, "PENDING");
        if (pendingClients.isEmpty()) {
            log.warn("[BulkApproval] No PENDING clients found for campaignId={}. Already sent?", campaignId);
            whatsAppNotificationService.sendBulkCampaignSummary(
                    campaignId, 0, 0, List.of("No PENDING clients found - already sent?"));
            return;
        }

        int sentCount = 0;
        int failedCount = 0;
        ArrayList<String> failedEmails = new ArrayList<>();

        for (CampaignClient client : pendingClients) {
            try {
                String personalizedSubject = personalize(subjectTemplate, client);
                String personalizedBody = personalize(bodyTemplate, client);
                GeneratedEmailDto personalizedEmail = GeneratedEmailDto.builder()
                        .subject(personalizedSubject)
                        .body(personalizedBody)
                        .build();

                String sentMessageId = sendWithFailover(client, personalizedEmail);

                client.setStatus("SENT");
                client.setSentMessageId(sentMessageId);
                client.setSentAt(LocalDateTime.now());
                campaignClientRepository.save(client);
                sentCount++;

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Bulk send interrupted", ie);
                }
            } catch (Exception e) {
                log.error("[BulkApproval] Failed for client={}: {}",
                        client.getClientEmail(), e.getMessage(), e);
                client.setStatus("FAILED");
                client.setErrorMessage(e.getMessage());
                campaignClientRepository.save(client);
                failedCount++;
                failedEmails.add(client.getClientEmail());
            }
        }

        log.info("[BulkApproval] Campaign complete. sent={} failed={}", sentCount, failedCount);
        whatsAppNotificationService.sendBulkCampaignSummary(campaignId, sentCount, failedCount, failedEmails);
    }

    private String sendWithFailover(
            CampaignClient client,
            GeneratedEmailDto email) {

        log.info("[BulkApproval] Sending to={} using Gmail sender",
                client.getClientEmail());

        return emailDeliveryService.send(
                client.getClientEmail(),
                email
        );
    }


    private String sendSingleWithFailover(
            EmailApprovalSession session,
            GeneratedEmailDto email,
            String inReplyTo,
            String references) {

        log.info("[Approval] Sending email to={} using Gmail sender",
                session.getClientEmail());

        return emailDeliveryService.send(
                session.getClientEmail(),
                email,
                inReplyTo,
                references
        );
    }

    private void handleRejection(EmailApprovalSession session, String feedback) {
        log.info("[Approval] REJECTED sessionId={} sessionType={} attempt={} feedback={}",
                session.getSessionId(), session.getSessionType(), session.getAttemptCount(), feedback);

        session.setAccumulatedFeedback(
                accumulateFeedback(session.getAccumulatedFeedback(), session.getAttemptCount(), feedback));
        session.setStatus("REGENERATING");
        session.setAttemptCount(session.getAttemptCount() + 1);
        sessionRepository.save(session);

        try {
            GeneratedEmailDto revised = regenerationService.regenerate(session);
            session.setCurrentSubject(revised.getSubject());
            session.setCurrentBody(revised.getBody());
            session.setStatus("PENDING_APPROVAL");
            sessionRepository.save(session);

            if ("CLIENT_REPLY".equals(session.getSessionType())) {
                whatsAppNotificationService.sendReplyForApproval(session);
            } else if ("BULK_CAMPAIGN".equals(session.getSessionType())) {
                whatsAppNotificationService.sendBulkForApproval(session);
            } else {
                whatsAppNotificationService.sendForApproval(session);
            }
        } catch (Exception e) {
            log.error("[Approval] Regeneration failed for session={}", session.getSessionId(), e);
            session.setStatus("REGENERATION_FAILED");
            sessionRepository.save(session);
            whatsAppNotificationService.sendRegenerationFailureAlert(session, e.getMessage());
        }
    }

    @Transactional
    public EmailApprovalSession initiateApprovalSession(GeneratedEmailDto email,
                                                        String clientName,
                                                        String clientEmail,
                                                        String fileId) {
        EmailApprovalSession session = new EmailApprovalSession();
        session.setClientName(clientName);
        session.setClientEmail(clientEmail);
        session.setCurrentSubject(email.getSubject());
        session.setCurrentBody(email.getBody());
        session.setFileId(fileId);
        session.setSessionType("CAMPAIGN");
        session.setStatus("PENDING_APPROVAL");

        EmailApprovalSession saved = sessionRepository.save(session);
        whatsAppNotificationService.sendForApproval(saved);

        log.info("[Approval] CAMPAIGN session created sessionId={} client={}",
                saved.getSessionId(), clientEmail);
        return saved;
    }

    @Transactional
    public EmailApprovalSession initiateBulkApprovalSession(GeneratedEmailDto email,
                                                            String campaignId,
                                                            int totalClients,
                                                            CampaignClient previewClient,
                                                            String fileId) {
        EmailApprovalSession session = new EmailApprovalSession();
        session.setClientName(previewClient.getClientName());
        session.setClientEmail(previewClient.getClientEmail());
        session.setCurrentSubject(email.getSubject());
        session.setCurrentBody(email.getBody());
        session.setFileId(fileId);
        session.setSessionType("BULK_CAMPAIGN");
        session.setStatus("PENDING_APPROVAL");
        session.setEmailThreadId(campaignId);
        session.setClientReplyContent(String.valueOf(totalClients));

        EmailApprovalSession saved = sessionRepository.save(session);
        whatsAppNotificationService.sendBulkForApproval(saved);

        log.info("[Approval] BULK_CAMPAIGN session created sessionId={} campaignId={} totalClients={}",
                saved.getSessionId(), campaignId, totalClients);
        return saved;
    }

    private String personalize(String template, CampaignClient client) {
        if (template == null) return "";
        String clientName = client.getClientName() != null ? client.getClientName() : "";
        String clientCompany = client.getClientCompany() != null ? client.getClientCompany() : "";
        return template.replace("{clientName}", clientName).replace("{clientCompany}", clientCompany);
    }

    private boolean isTerminalStatus(String status) {
        return "APPROVED".equals(status) || "EXPIRED".equals(status) || "REGENERATION_FAILED".equals(status);
    }

    private String accumulateFeedback(String existing, int attemptNumber, String newFeedback) {
        String entry = "[Round " + attemptNumber + "]: " + newFeedback;
        return (existing == null || existing.isBlank()) ? entry : existing + "\n" + entry;
    }

    private String normalizeReply(String replyText) {
        return replyText == null ? "" : replyText.trim();
    }

    private boolean isApproveCommand(String normalizedReply) {
        if (normalizedReply == null || normalizedReply.isBlank()) return false;
        String upper = normalizedReply.toUpperCase();
        if (APPROVE_KEYWORD.equals(upper)) return true;
        String alphaNum = upper.replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return alphaNum.matches(".*\\bAPPROVE\\b.*");
    }
}

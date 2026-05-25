package com.askoxy.emailautomation.service;

import com.askoxy.emailautomation.agent.ClientIntelligenceAgent;
import com.askoxy.emailautomation.agent.ComplianceAgent;
import com.askoxy.emailautomation.agent.EmailGenerationAgent;
import com.askoxy.emailautomation.agent.OpportunityMatchingAgent;
import com.askoxy.emailautomation.agent.StrategyAgent;
import com.askoxy.emailautomation.dto.EmailAutomationDto;
import com.askoxy.emailautomation.dto.GeneratedEmailDto;
import com.askoxy.emailautomation.entity.CampaignClient;
import com.askoxy.emailautomation.entity.EmailApprovalSession;
import com.askoxy.emailautomation.entity.UploadedFile;
import com.askoxy.emailautomation.repository.CampaignClientRepository;
import com.askoxy.emailautomation.repository.EmailApprovalSessionRepository;
import com.askoxy.emailautomation.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAutomationService {

    private final RetrievalService retrievalService;
    private final UploadedFileRepository uploadedFileRepository;
    private final EmailApprovalSessionRepository sessionRepository;
    private final CampaignClientRepository campaignClientRepository;
    private final ApprovalOrchestrationService approvalOrchestrationService;

    private final ClientIntelligenceAgent clientIntelligenceAgent;
    private final OpportunityMatchingAgent opportunityMatchingAgent;
    private final StrategyAgent strategyAgent;
    private final EmailGenerationAgent emailGenerationAgent;
    private final ComplianceAgent complianceAgent;

    @Transactional
    public EmailAutomationDto startCampaign(String clientName, String clientEmail) {
        UploadedFile uploadedFile = uploadedFileRepository
                .findTopByUploadStatusOrderByCreatedAtDesc("COMPLETED")
                .orElseThrow(() -> new RuntimeException(
                        "No completed file found. Please upload a PDF first."));

        log.info("[Campaign] Using fileId={} fileName={} for client={}",
                uploadedFile.getFileId(), uploadedFile.getFileName(), clientEmail);

        List<EmailApprovalSession> staleSessions = sessionRepository
                .findAllByClientEmailAndStatusIn(
                        clientEmail, List.of("PENDING_APPROVAL", "REGENERATING", "QUEUED"));
        if (!staleSessions.isEmpty()) {
            staleSessions.forEach(s -> {
                s.setStatus("EXPIRED");
                log.warn("[Campaign] Expiring stale session={} for client={}",
                        s.getSessionId(), clientEmail);
            });
            sessionRepository.saveAll(staleSessions);
        }

        String context = retrievalService.retrieve(
                "company overview business model offerings products services",
                uploadedFile.getVectorStoreId(), 10);

        String intelligence = clientIntelligenceAgent.analyze(clientName, context);
        String opportunity = opportunityMatchingAgent.findOpportunity(clientName, intelligence);
        String strategy = strategyAgent.buildStrategy(clientName, opportunity);

        GeneratedEmailDto rawEmail = emailGenerationAgent.generateEmail(
                clientName, intelligence, strategy, context);

        GeneratedEmailDto finalEmail = complianceAgent.reviewAndRefine(rawEmail);

        approvalOrchestrationService.initiateApprovalSession(
                finalEmail, clientName, clientEmail, uploadedFile.getFileId());

        log.info("[Campaign] Pending admin approval for clientEmail={}", clientEmail);

        return EmailAutomationDto.builder()
                .success(true)
                .message("Email generated and sent to admin for WhatsApp approval")
                .clientEmail(clientEmail)
                .generatedEmail(finalEmail)
                .build();
    }

    @Transactional
    public EmailAutomationDto startBulkCampaign(String campaignId) {
        List<CampaignClient> pendingClients = campaignClientRepository
                .findByCampaignIdAndStatus(campaignId, "PENDING");

        if (pendingClients.isEmpty()) {
            throw new RuntimeException(
                    "No PENDING clients found for campaignId=" + campaignId
                            + ". Either the campaignId is wrong or all clients are already sent.");
        }

        log.info("[BulkCampaign] Starting campaign={} with {} pending clients",
                campaignId, pendingClients.size());

        CampaignClient previewClient = pendingClients.get(0);
        log.info("[BulkCampaign] Preview client: {} <{}>",
                previewClient.getClientName(), previewClient.getClientEmail());

        UploadedFile uploadedFile = uploadedFileRepository
                .findTopByUploadStatusOrderByCreatedAtDesc("COMPLETED")
                .orElseThrow(() -> new RuntimeException(
                        "No completed file found. Please upload a PDF first."));

        log.info("[BulkCampaign] Using fileId={} fileName={}",
                uploadedFile.getFileId(), uploadedFile.getFileName());

        String context = retrievalService.retrieve(
                "company overview business model offerings products services",
                uploadedFile.getVectorStoreId(), 10);

        String intelligence = clientIntelligenceAgent.analyze(
                previewClient.getClientName(), context);
        String opportunity = opportunityMatchingAgent.findOpportunity(
                previewClient.getClientName(), intelligence);
        String strategy = strategyAgent.buildStrategy(
                previewClient.getClientName(), opportunity);

        GeneratedEmailDto rawEmail = emailGenerationAgent.generateBulkEmail(
                previewClient.getClientName(),
                previewClient.getClientCompany(),
                intelligence, strategy, context);

        GeneratedEmailDto finalEmail = complianceAgent.reviewAndRefine(rawEmail);

        log.info("[BulkCampaign] AI generated template. Subject='{}'", finalEmail.getSubject());

        approvalOrchestrationService.initiateBulkApprovalSession(
                finalEmail,
                campaignId,
                pendingClients.size(),
                previewClient,
                uploadedFile.getFileId()
        );

        log.info("[BulkCampaign] BULK_CAMPAIGN session created. Pending WhatsApp admin approval.");

        return EmailAutomationDto.builder()
                .success(true)
                .message("Bulk campaign email generated for " + pendingClients.size()
                        + " clients. Sent to admin for WhatsApp approval.")
                .clientEmail(previewClient.getClientEmail())
                .generatedEmail(finalEmail)
                .build();
    }
}

package com.askoxy.emailautomation.service;

import com.askoxy.emailautomation.dto.GeneratedEmailDto;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Handles all outbound email delivery.
 *
 * Two modes:
 *
 *  1. SINGLE-SENDER (existing CLIENT_REPLY / old CAMPAIGN flow)
 *     send(toEmail, emailDto, inReplyTo, references)
 *     → uses the default injected JavaMailSender (spring.mail.*)
 *
 *  2. MULTI-SENDER (new BULK_CAMPAIGN flow)
 *     send(mailSender, fromEmail, fromName, toEmail, emailDto)
 *     → uses the caller-supplied JavaMailSender (one per company account)
 *
 * The UUID-based Message-ID fix is applied in both modes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    /** Default single sender — used for CLIENT_REPLY and legacy CAMPAIGN sessions */
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String defaultFromEmail;

    @Value("${app.email.from-name}")
    private String defaultFromName;

    // ── Mode 1: Single-sender (existing flow — unchanged) ─────────────────────

    public String send(String toEmail, GeneratedEmailDto email) {
        return send(toEmail, email, null, null);
    }

    public String send(String toEmail, GeneratedEmailDto email,
                       String inReplyTo, String references) {
        return sendInternal(
                mailSender, defaultFromEmail, defaultFromName,
                toEmail, email.getSubject(), email.getBody(),
                inReplyTo, references
        );
    }

    // ── Mode 2: Multi-sender (new BULK_CAMPAIGN flow) ─────────────────────────

    /**
     * Sends email using a specific JavaMailSender (one of the 6 company accounts).
     *
     * @param sender    The JavaMailSender to use (from MultiSenderConfig)
     * @param fromEmail The sender's email address (for the From header)
     * @param fromName  The sender's display name (for the From header)
     * @param toEmail   Recipient email
     * @param email     Subject + body DTO
     * @return          The Gmail Message-ID of the sent email
     */
    public String send(JavaMailSender sender,
                       String fromEmail,
                       String fromName,
                       String toEmail,
                       GeneratedEmailDto email) {
        return sendInternal(
                sender, fromEmail, fromName,
                toEmail, email.getSubject(), email.getBody(),
                null, null
        );
    }

    // ── Core internal sender ──────────────────────────────────────────────────

    private String sendInternal(JavaMailSender sender,
                                String fromEmail,
                                String fromName,
                                String toEmail,
                                String subject,
                                String body,
                                String inReplyTo,
                                String references) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, false);

            if (inReplyTo != null && !inReplyTo.isBlank()) {
                message.setHeader("In-Reply-To", inReplyTo.trim());
            }
            if (references != null && !references.isBlank()) {
                message.setHeader("References", references.trim());
            }

            // ── UUID-based Message-ID fix ──────────────────────────────────────
            // Generate a proper RFC-compliant Message-ID BEFORE saveChanges()
            // so we always get a real ID (not @LAPTOP-xxx from local hostname).
            String domain = fromEmail.contains("@")
                    ? fromEmail.substring(fromEmail.indexOf('@') + 1)
                    : "mail.oxyglobal.com";

            String customMessageId = "<" + UUID.randomUUID() + "@" + domain + ">";
            message.setHeader("Message-ID", customMessageId);
            message.saveChanges();

            sender.send(message);

            // Re-read after saveChanges to get the final committed value
            String sentMessageId = message.getMessageID();
            if (sentMessageId == null || sentMessageId.isBlank()) {
                sentMessageId = customMessageId;
            }

            log.info("[EmailDelivery] Sent → to={} from={} messageId={} inReplyTo={}",
                    toEmail, fromEmail, sentMessageId, inReplyTo);

            return sentMessageId;

        } catch (Exception ex) {
            throw new RuntimeException(
                    "Failed to send email to " + toEmail + " from " + fromEmail, ex);
        }
    }
}
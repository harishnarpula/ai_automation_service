package com.aiautomationservice.service;

import com.aiautomationservice.dto.GeneratedEmailDto;
import com.aiautomationservice.service.EmailTemplateService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private final JavaMailSender mailSender;
    private final EmailTemplateService emailTemplateService;  // ← NEW

    @Value("${app.email.from-address:${spring.mail.username}}")
    private String defaultFromAddress;

    @Value("${app.email.from-name:ASKOXY.AI TEAM}")
    private String defaultFromName;

    // ── Mode 1: Single-sender ─────────────────────────────────────────────────

    public String send(String toEmail, GeneratedEmailDto email) {
        return send(toEmail, email, null, null);
    }

    public String send(String toEmail, GeneratedEmailDto email,
                       String inReplyTo, String references) {
        return sendInternal(
                mailSender, defaultFromAddress, defaultFromName,
                toEmail, email.getSubject(), email.getBody(),
                inReplyTo, references
        );
    }

    // ── Mode 2: Multi-sender ──────────────────────────────────────────────────

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
            // Extract client first name from greeting line for template context
            String clientName = extractClientName(body);

            // Wrap plain-text AI body in branded HTML template
            String htmlBody = emailTemplateService.wrapInTemplate(body, clientName);

            MimeMessage message = sender.createMimeMessage();
            // true = multipart (needed for HTML)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(subject);

            // Send HTML version + plain text fallback
            helper.setText(body, htmlBody);  // (plainText, htmlText)

            if (inReplyTo != null && !inReplyTo.isBlank()) {
                message.setHeader("In-Reply-To", inReplyTo.trim());
            }
            if (references != null && !references.isBlank()) {
                message.setHeader("References", references.trim());
            }

            // UUID-based Message-ID — no @LAPTOP leak
            String domain = fromEmail.contains("@")
                    ? fromEmail.substring(fromEmail.indexOf('@') + 1)
                    : "oxyglobal.ai";
            String customMessageId = "<" + UUID.randomUUID() + "@" + domain + ">";
            message.setHeader("Message-ID", customMessageId);
            message.saveChanges();

            sender.send(message);

            String sentMessageId = message.getMessageID();
            if (sentMessageId == null || sentMessageId.isBlank()) {
                sentMessageId = customMessageId;
            }

            log.info("[EmailDelivery] ✅ Sent HTML email → to={} from={} messageId={} inReplyTo={}",
                    toEmail, fromEmail, sentMessageId, inReplyTo);

            return sentMessageId;

        } catch (Exception ex) {
            log.error("""
                [EmailDelivery] ❌ SMTP FAILED
                to={}
                from={}
                exception={}
                message={}
                cause={}
                """,
                    toEmail,
                    fromEmail,
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().getMessage() : "null",
                    ex
            );
            throw new RuntimeException("SMTP failure: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extracts the client's first name from the greeting line.
     * e.g. "Hi Rishi," → "Rishi"
     * Falls back to empty string if not found.
     */
    private String extractClientName(String body) {
        if (body == null) return "";
        String[] lines = body.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("hi ")) {
                return trimmed.substring(3).replaceAll("[,!].*", "").trim();
            }
            if (trimmed.toLowerCase().startsWith("dear ")) {
                return trimmed.substring(5).replaceAll("[,!].*", "").trim();
            }
        }
        return "";
    }
}
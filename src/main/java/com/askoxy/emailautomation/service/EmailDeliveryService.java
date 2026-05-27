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

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from-address:${spring.mail.username}}")
    private String defaultFromAddress;

    @Value("${app.email.from-name:AskOxy Team}")
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

            log.info("[EmailDelivery] ✅ Sent → to={} from={} messageId={} inReplyTo={}",
                    toEmail, fromEmail, sentMessageId, inReplyTo);

            return sentMessageId;

        } catch (Exception ex) {
            log.error("[EmailDelivery] ❌ Failed → to={} from={}", toEmail, fromEmail, ex);
            throw new RuntimeException(
                    "Failed to send email to " + toEmail + " from " + fromEmail, ex);
        }
    }
}
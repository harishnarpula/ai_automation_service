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

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.from-name}")
    private String fromName;

    // ── Derive a stable domain from our sending address ───────────────────────
    // e.g. "venagantirishi@gmail.com" → "gmail.com"
    // Used to build a portable Message-ID that works on any host (local or Railway).
    private String senderDomain() {
        if (fromEmail != null && fromEmail.contains("@")) {
            return fromEmail.substring(fromEmail.indexOf('@') + 1);
        }
        return "mailautomation.app";
    }

    // ── Convenience overload (no threading headers) ───────────────────────────
    public String send(String toEmail, GeneratedEmailDto email) {
        return send(toEmail, email, null, null);
    }

    /**
     * Sends an email and returns the Message-ID we stamped on it.
     *
     * FIX: We now generate the Message-ID ourselves using UUID + sender domain
     * BEFORE calling saveChanges() / send().  This guarantees:
     *   1. The ID never contains "@LAPTOP-xxx" (local hostname leak).
     *   2. The exact same ID is persisted in sent_message_id in the DB.
     *   3. When the client hits Reply, their email's In-Reply-To will match
     *      what's stored in DB → GmailPollingService GUARD 4 passes correctly.
     *
     * @param toEmail    recipient address
     * @param email      subject + body DTO
     * @param inReplyTo  Message-ID of email we are replying to (null for campaigns)
     * @param references References header value (null for campaigns)
     * @return the Message-ID stamped on the sent email
     */
    public String send(String toEmail, GeneratedEmailDto email,
                       String inReplyTo, String references) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject(email.getSubject());
            helper.setText(email.getBody(), false);

            // ── Threading headers (CLIENT_REPLY sessions only) ────────────────
            if (inReplyTo != null && !inReplyTo.isBlank()) {
                message.setHeader("In-Reply-To", inReplyTo.trim());
            }
            if (references != null && !references.isBlank()) {
                message.setHeader("References", references.trim());
            }

            // ── FIX: Set a deterministic Message-ID BEFORE saveChanges() ─────
            // Format: <UUID@sender-domain>
            // e.g.  : <550e8400-e29b-41d4-a716-446655440000@gmail.com>
            //
            // Why not use saveChanges() default?
            //   saveChanges() builds the Message-ID from InetAddress.getLocalHost()
            //   which gives "@LAPTOP-QO76BQ" locally and something unpredictable
            //   on Railway containers. We own the ID instead.
            // ─────────────────────────────────────────────────────────────────
            String customMessageId = "<" + UUID.randomUUID() + "@" + senderDomain() + ">";
            message.setHeader("Message-ID", customMessageId);

            // saveChanges() finalises MIME structure; Message-ID header is already set
            // so it won't be overwritten.
            message.saveChanges();

            // Verify our header survived saveChanges()
            String[] ids = message.getHeader("Message-ID");
            String sentMessageId = (ids != null && ids.length > 0) ? ids[0].trim() : customMessageId;

            mailSender.send(message);

            log.info("[EmailDelivery] ✅ Email sent — to={} messageId={} inReplyTo={}",
                    toEmail, sentMessageId, inReplyTo);

            return sentMessageId;

        } catch (Exception ex) {
            log.error("[EmailDelivery] ❌ Failed to send email to={}", toEmail, ex);
            throw new RuntimeException("Failed to send email to " + toEmail, ex);
        }
    }
}
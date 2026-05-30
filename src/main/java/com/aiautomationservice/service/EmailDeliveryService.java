package com.aiautomationservice.service;

import com.aiautomationservice.dto.GeneratedEmailDto;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeliveryService {

    private final EmailTemplateService emailTemplateService;

    @Value("${app.gmail.client-id}")
    private String clientId;

    @Value("${app.gmail.client-secret}")
    private String clientSecret;

    @Value("${app.gmail.refresh-token}")
    private String refreshToken;

    @Value("${app.gmail.sender-address}")
    private String senderAddress;

    @Value("${app.gmail.sender-name:ASKOXY.AI TEAM}")
    private String senderName;

    // ── Public API ────────────────────────────────────────────────────────────

    public String send(String toEmail, GeneratedEmailDto email) {
        return send(toEmail, email, null, null);
    }

    public String send(String toEmail, GeneratedEmailDto email,
                       String inReplyTo, String references) {
        return sendViaGmailApi(toEmail, email.getSubject(), email.getBody(), inReplyTo, references);
    }

    // ── Core sender ───────────────────────────────────────────────────────────

    private String sendViaGmailApi(String toEmail,
                                   String subject,
                                   String body,
                                   String inReplyTo,
                                   String references) {
        log.info("[GmailAPI] ═══════════════ SEND START ═══════════════");
        log.info("[GmailAPI] 📤 to={}", toEmail);
        log.info("[GmailAPI] 📤 subject={}", subject);
        log.info("[GmailAPI] 📤 inReplyTo={} | references={}", inReplyTo, references);

        try {
            // STEP 1 — OAuth2 + Gmail service
            log.info("[GmailAPI] STEP 1 → Building Gmail OAuth2 service...");
            Gmail gmailService = buildGmailService();
            log.info("[GmailAPI] STEP 1 ✅ Gmail service ready");

            // STEP 2 — HTML template wrapping
            log.info("[GmailAPI] STEP 2 → Wrapping body in HTML template...");
            String clientName = extractClientName(body);
            log.info("[GmailAPI] STEP 2 → clientName extracted='{}'", clientName);
            String htmlBody = emailTemplateService.wrapInTemplate(body, clientName);
            log.info("[GmailAPI] STEP 2 ✅ HTML ready, length={} chars", htmlBody.length());

            // STEP 3 — Build MimeMessage
            log.info("[GmailAPI] STEP 3 → Building MimeMessage...");
            MimeMessage mimeMessage = buildMimeMessage(
                    toEmail, subject, body, htmlBody, inReplyTo, references);
            log.info("[GmailAPI] STEP 3 ✅ MimeMessage built");

            // STEP 4 — Encode to Gmail raw format
            log.info("[GmailAPI] STEP 4 → Encoding to Gmail raw Base64...");
            Message gmailMessage = toGmailMessage(mimeMessage);
            log.info("[GmailAPI] STEP 4 ✅ Encoded, raw size={} bytes", gmailMessage.getRaw().length());

            // STEP 5 — Thread resolution (only for replies)
            if (inReplyTo != null && !inReplyTo.isBlank()) {
                log.info("[GmailAPI] STEP 5 → Resolving threadId for inReplyTo={}", inReplyTo);
                String threadId = resolveThreadId(gmailService, inReplyTo);
                if (threadId != null) {
                    gmailMessage.setThreadId(threadId);
                    log.info("[GmailAPI] STEP 5 ✅ threadId={} set on message", threadId);
                } else {
                    log.warn("[GmailAPI] STEP 5 ⚠️ No threadId found — will send as new thread");
                }
            } else {
                log.info("[GmailAPI] STEP 5 → Skipped (new email, not a reply)");
            }

            // STEP 6 — Send via Gmail API
            log.info("[GmailAPI] STEP 6 → Calling Gmail API users.messages.send...");
            Message sent = gmailService.users()
                    .messages()
                    .send("me", gmailMessage)
                    .execute();
            log.info("[GmailAPI] STEP 6 ✅ Accepted by Google → gmailId={} threadId={}",
                    sent.getId(), sent.getThreadId());

            String messageId = "<" + sent.getId() + "@mail.gmail.com>";
            log.info("[GmailAPI] ═══════════ SEND SUCCESS → messageId={} ═══════════", messageId);
            return messageId;

        } catch (Exception ex) {
            log.error("[GmailAPI] ═══════════════ SEND FAILED ═══════════════");
            log.error("[GmailAPI] ❌ to={}  subject={}", toEmail, subject);
            log.error("[GmailAPI] ❌ Exception  : {}", ex.getClass().getName());
            log.error("[GmailAPI] ❌ Message    : {}", ex.getMessage());
            if (ex.getCause() != null) {
                log.error("[GmailAPI] ❌ Cause      : {}", ex.getCause().getMessage());
                if (ex.getCause().getCause() != null) {
                    log.error("[GmailAPI] ❌ Root cause : {}", ex.getCause().getCause().getMessage());
                }
            }
            log.error("[GmailAPI] ❌ Stacktrace:", ex);
            throw new RuntimeException("Gmail API failure: " + ex.getMessage(), ex);
        }
    }

    // ── Gmail service builder (OAuth2) ────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private Gmail buildGmailService() throws Exception {
        // Env var presence check
        log.info("[GmailAPI] 🔑 clientId       : {}",
                clientId != null ? clientId.substring(0, Math.min(20, clientId.length())) + "..." : "❌ NULL");
        log.info("[GmailAPI] 🔑 clientSecret   : {}",
                clientSecret != null && !clientSecret.isBlank() ? "✅ present" : "❌ NULL/EMPTY");
        log.info("[GmailAPI] 🔑 refreshToken   : {}",
                refreshToken != null && !refreshToken.isBlank() ? "✅ present" : "❌ NULL/EMPTY");
        log.info("[GmailAPI] 🔑 senderAddress  : {}", senderAddress);

        // Hard fail fast if any credential is missing
        if (clientId == null || clientId.isBlank())
            throw new IllegalStateException("❌ GMAIL_CLIENT_ID missing — add to Railway Variables");
        if (clientSecret == null || clientSecret.isBlank())
            throw new IllegalStateException("❌ GMAIL_CLIENT_SECRET missing — add to Railway Variables");
        if (refreshToken == null || refreshToken.isBlank())
            throw new IllegalStateException("❌ GMAIL_REFRESH_TOKEN missing — add to Railway Variables");

        log.info("[GmailAPI] 🔑 Building GoogleCredential...");
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setRefreshToken(refreshToken);

        log.info("[GmailAPI] 🔑 Calling Google token endpoint (refreshToken → accessToken)...");
        boolean refreshed = credential.refreshToken();
        log.info("[GmailAPI] 🔑 Token refreshed={} | accessToken present={}",
                refreshed, credential.getAccessToken() != null);

        if (!refreshed || credential.getAccessToken() == null) {
            throw new IllegalStateException(
                    "❌ OAuth2 token refresh FAILED — check GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET, GMAIL_REFRESH_TOKEN are valid and not expired");
        }

        log.info("[GmailAPI] 🔑 OAuth2 handshake successful ✅");
        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("AskOxy-MailAutomation")
                .build();
    }

    // ── MimeMessage builder ───────────────────────────────────────────────────

    private MimeMessage buildMimeMessage(String toEmail,
                                         String subject,
                                         String plainText,
                                         String htmlText,
                                         String inReplyTo,
                                         String references)
            throws MessagingException, UnsupportedEncodingException {

        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session);

        message.setFrom(new InternetAddress(senderAddress, senderName));
        message.setRecipients(MimeMessage.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject, "UTF-8");

        MimeMultipart multipart = new MimeMultipart("alternative");

        MimeBodyPart plainPart = new MimeBodyPart();
        plainPart.setText(plainText, "UTF-8", "plain");

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setText(htmlText, "UTF-8", "html");

        multipart.addBodyPart(plainPart);
        multipart.addBodyPart(htmlPart);
        message.setContent(multipart);

        if (inReplyTo != null && !inReplyTo.isBlank())
            message.setHeader("In-Reply-To", inReplyTo.trim());
        if (references != null && !references.isBlank())
            message.setHeader("References", references.trim());

        String domain = senderAddress.contains("@")
                ? senderAddress.substring(senderAddress.indexOf('@') + 1)
                : "gmail.com";
        message.setHeader("Message-ID", "<" + UUID.randomUUID() + "@" + domain + ">");
        message.saveChanges();

        return message;
    }

    // ── Convert MimeMessage → Gmail API Message ───────────────────────────────

    private Message toGmailMessage(MimeMessage mimeMessage) throws IOException, MessagingException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);
        String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    // ── Resolve Gmail threadId from messageId ─────────────────────────────────

    private String resolveThreadId(Gmail gmailService, String messageId) {
        try {
            String cleanId = messageId.replaceAll("[<>]", "").trim();
            log.info("[GmailAPI] 🔍 threadId lookup → rfc822msgid:{}", cleanId);

            var listResponse = gmailService.users().messages()
                    .list("me")
                    .setQ("rfc822msgid:" + cleanId)
                    .execute();

            if (listResponse.getMessages() != null && !listResponse.getMessages().isEmpty()) {
                String gmailMsgId = listResponse.getMessages().get(0).getId();
                Message original = gmailService.users().messages()
                        .get("me", gmailMsgId)
                        .setFormat("minimal")
                        .execute();
                log.info("[GmailAPI] 🔍 threadId found={}", original.getThreadId());
                return original.getThreadId();
            }
            log.warn("[GmailAPI] 🔍 No message matched rfc822msgid:{}", cleanId);
        } catch (Exception e) {
            log.warn("[GmailAPI] 🔍 threadId lookup failed for messageId={} : {}", messageId, e.getMessage());
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractClientName(String body) {
        if (body == null) return "";
        for (String line : body.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("hi "))
                return trimmed.substring(3).replaceAll("[,!].*", "").trim();
            if (trimmed.toLowerCase().startsWith("dear "))
                return trimmed.substring(5).replaceAll("[,!].*", "").trim();
        }
        return "";
    }
}
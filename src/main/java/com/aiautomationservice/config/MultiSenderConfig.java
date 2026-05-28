package com.aiautomationservice.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads all sender accounts from application.properties (app.senders[]) and
 * builds a JavaMailSender for each one.
 *
 * All accounts are GoDaddy business emails — uses GoDaddy SMTP:
 *   Host: smtpout.secureserver.net
 *   Port: 587
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "app")
public class MultiSenderConfig {

    @Getter
    private final List<SenderProperties> senders = new ArrayList<>();

    private final Map<String, JavaMailSender> senderMap = new LinkedHashMap<>();
    private final Map<String, String> nameMap = new LinkedHashMap<>();

    @Getter
    private final List<String> senderEmails = new ArrayList<>();

    // ── GoDaddy SMTP constants ────────────────────────────────────────────────
    private static final String GODADDY_SMTP_HOST = "smtpout.secureserver.net";
    private static final int    GODADDY_SMTP_PORT = 587;

    @PostConstruct
    public void initializeSenders() {
        if (senders.isEmpty()) {
            throw new IllegalStateException(
                    "[MultiSenderConfig] No senders configured! " +
                            "Add app.senders[0].* entries in application.properties.");
        }

        for (SenderProperties sender : senders) {
            if (sender.getEmail() == null || sender.getEmail().isBlank()) {
                log.warn("[MultiSenderConfig] Skipping sender with null/blank email.");
                continue;
            }

            JavaMailSender mailSender = buildMailSender(sender);
            senderMap.put(sender.getEmail(), mailSender);
            nameMap.put(sender.getEmail(),
                    sender.getName() != null ? sender.getName() : sender.getEmail());
            senderEmails.add(sender.getEmail());

            log.info("[MultiSenderConfig] Registered GoDaddy sender: {} ({})",
                    sender.getEmail(), sender.getName());
        }

        log.info("[MultiSenderConfig] Total senders loaded: {}", senderMap.size());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public JavaMailSender getSenderFor(String email) {
        JavaMailSender sender = senderMap.get(email);
        if (sender == null) {
            throw new IllegalArgumentException(
                    "[MultiSenderConfig] No JavaMailSender registered for: " + email);
        }
        return sender;
    }

    public String getSenderName(String email) {
        return nameMap.getOrDefault(email, email);
    }

    public String getSenderEmailForIndex(int clientIndex) {
        if (senderEmails.isEmpty()) {
            throw new IllegalStateException("[MultiSenderConfig] No senders available.");
        }
        return senderEmails.get(clientIndex % senderEmails.size());
    }

    // ── GoDaddy SMTP builder ──────────────────────────────────────────────────

    private JavaMailSender buildMailSender(SenderProperties props) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost(GODADDY_SMTP_HOST);
        mailSender.setPort(GODADDY_SMTP_PORT);
        mailSender.setUsername(props.getEmail());
        mailSender.setPassword(props.getPassword());

        Properties javaMailProps = mailSender.getJavaMailProperties();
        javaMailProps.put("mail.smtp.auth",                "true");
        javaMailProps.put("mail.smtp.starttls.enable",     "true");
        javaMailProps.put("mail.smtp.starttls.required",   "true");
        javaMailProps.put("mail.smtp.connectiontimeout",   "10000");
        javaMailProps.put("mail.smtp.timeout",             "10000");
        javaMailProps.put("mail.smtp.writetimeout",        "10000");

        return mailSender;
    }
}

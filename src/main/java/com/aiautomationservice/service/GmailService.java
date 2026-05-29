package com.aiautomationservice.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GmailService {

    private final JavaMailSender mailSender;

    public GmailService(@org.springframework.beans.factory.annotation.Qualifier("radhaAiMailSender") JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${radha.email.from}")
    private String fromEmail;

    @Value("${radha.email.to}")
    private String toEmail;

    public String send(String subject, String content,
                       String imageUrl) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(
                    subject != null ? subject : "📢 New Content");

            String html = """
                <html>
                <body style="font-family:Arial,sans-serif;
                             max-width:600px;margin:auto;
                             padding:20px">
                  <h2 style="color:#1a73e8">%s</h2>
                  %s
                  <div style="white-space:pre-wrap;
                              line-height:1.7;
                              font-size:15px">%s</div>
                  <hr style="margin-top:30px"/>
                  <p style="color:#999;font-size:12px">
                    Sent by Radha AI — AskOxy Group
                  </p>
                </body>
                </html>
                """.formatted(
                    subject != null ? subject : "",
                    imageUrl != null && !imageUrl.isBlank()
                            ? "<img src='" + imageUrl
                            + "' style='width:100%;border-radius:"
                            + "8px;margin:16px 0'/>"
                            : "",
                    content
            );

            helper.setText(html, true);
            mailSender.send(msg);
            log.info("Email sent to {}", toEmail);
            return "SENT";

        } catch (Exception ex) {
            log.error("Email failed: {}", ex.getMessage());
            return "FAILED: " + ex.getMessage();
        }
    }
    // ── NEW: Send email directly to a lead's email address ───────────────────

    /**
     * Sends an HTML email to the lead's own email address.
     * Used for thank-you / enrollment confirmation emails.
     *
     * @param toLeadEmail  lead's email address
     * @param subject      email subject line
     * @param htmlContent  full HTML body content
     */
    public String sendToLead(String toLeadEmail, String subject, String htmlContent) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toLeadEmail);
            helper.setSubject(subject != null ? subject : "Thank you for enrolling!");
            helper.setText(htmlContent, true);

            mailSender.send(msg);
            log.info("[GmailService] Thank-you email sent to lead: {}", toLeadEmail);
            return "SENT";

        } catch (Exception ex) {
            log.error("[GmailService] Failed to send email to lead {}: {}", toLeadEmail, ex.getMessage());
            return "FAILED: " + ex.getMessage();
        }
    }

}

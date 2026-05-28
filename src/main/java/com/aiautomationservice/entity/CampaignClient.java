package com.aiautomationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents one client in a bulk email campaign.
 *
 * Table: campaign_clients
 *
 * Status flow:
 *   PENDING → SENT     (email delivered successfully)
 *   PENDING → FAILED   (delivery error — errorMessage populated)
 *
 * Multiple clients share the same campaignId (one CSV upload = one campaign).
 * assignedSender is set at upload time via round-robin across 6 company emails.
 */
@Entity
@Table(name = "campaign_clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Client Info ───────────────────────────────────────────────────────────

    @Column(name = "client_name", nullable = false, length = 255)
    private String clientName;

    @Column(name = "client_company", nullable = false, length = 255)
    private String clientCompany;

    @Column(name = "client_email", nullable = false, length = 255)
    private String clientEmail;

    // ── Campaign Grouping ─────────────────────────────────────────────────────

    /**
     * Groups all clients uploaded together in one CSV.
     * Generated at upload time (UUID). Used to trigger the campaign later.
     */
    @Column(name = "campaign_id", nullable = false, length = 100)
    private String campaignId;

    // ── Sender Assignment ─────────────────────────────────────────────────────

    /**
     * The company email address assigned to send to this client.
     * Assigned via round-robin at CSV upload time.
     * e.g. company2@gmail.com
     */
    @Column(name = "assigned_sender", nullable = false, length = 255)
    private String assignedSender;

    // ── Send Status ───────────────────────────────────────────────────────────

    /**
     * PENDING  → not yet sent (default)
     * SENT     → delivered successfully
     * FAILED   → delivery failed (see errorMessage)
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /** Populated on FAILED — the exception message from JavaMail */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** The Gmail Message-ID of the email we sent to this client */
    @Column(name = "sent_message_id", length = 500)
    private String sentMessageId;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
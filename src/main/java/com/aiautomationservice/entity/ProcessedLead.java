package com.aiautomationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Tracks processed leads to prevent duplicate WhatsApp messages.
 * A lead is identified by phone number.
 * rowNumber is stored for debugging/tracing Google Sheet row.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_leads",
        uniqueConstraints = @UniqueConstraint(columnNames = "phone"))
public class ProcessedLead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Plain digits only e.g. 916281565528 */
    @Column(nullable = false, unique = true)
    private String phone;

    /** Google Sheet row number — for tracing */
    @Column
    private Integer rowNumber;

    /** Lead name at time of first contact */
    @Column
    private String name;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.processedAt = LocalDateTime.now();
    }
}
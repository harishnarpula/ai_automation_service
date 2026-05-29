package com.aiautomationservice.config;

// ─────────────────────────────────────────────────────────────────────────────
// NEW FILE — WhatsApp Lead Flow
// Binds `ultramsg.*` from application.yml into a typed Spring config bean.
// Does NOT touch AppConfig.java or any existing config.
// ─────────────────────────────────────────────────────────────────────────────

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "sanjana-ultramsg")
public class UltraMsgProperties {

    /** UltraMsg instance ID — from UltraMsg dashboard */
    @NotBlank(message = "UltraMsg instance ID must be configured")
    private String instanceId;

    /** UltraMsg API token — from UltraMsg dashboard */
    @NotBlank(message = "UltraMsg token must be configured")
    private String token;

    /** UltraMsg REST base URL — default: https://api.ultramsg.com */
    @NotBlank(message = "UltraMsg base URL must be configured")
    private String baseUrl;

    /** Team WhatsApp group ID — ends with @g.us (UltraMsg → Contacts → Groups) */
    @NotBlank(message = "Team group ID must be configured")
    private String teamGroupId;
}
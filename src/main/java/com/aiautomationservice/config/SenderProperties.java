package com.aiautomationservice.config;

import lombok.Data;

/**
 * Represents one company sender email account.
 * Bound from application.properties: app.senders[N].*
 */
@Data
public class SenderProperties {

    /** Full Gmail address e.g. company1@gmail.com */
    private String email;

    /** Gmail App Password (16-char, no spaces) */
    private String password;

    /** Display name shown in the From header e.g. "OxyGlobal Sales" */
    private String name;
}
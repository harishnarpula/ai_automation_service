package com.aiautomationservice.exception;


// ─────────────────────────────────────────────────────────────────────────────
// NEW FILE — WhatsApp Lead Flow
// Custom exception thrown when UltraMsg API returns an error or is unreachable.
// Caught by GlobalExceptionHandler (add handler there if you have one already).
// ─────────────────────────────────────────────────────────────────────────────

public class UltraMsgException extends RuntimeException {

    /** HTTP status code returned by UltraMsg, or -1 for network errors */
    private final int statusCode;

    /** Use this when UltraMsg returns a non-2xx HTTP response */
    public UltraMsgException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /** Use this when the HTTP call itself fails (IOException, timeout, etc.) */
    public UltraMsgException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
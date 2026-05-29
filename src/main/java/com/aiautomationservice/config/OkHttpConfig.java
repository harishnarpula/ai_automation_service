package com.aiautomationservice.config;

// ─────────────────────────────────────────────────────────────────────────────
// NEW FILE — WhatsApp Lead Flow
// Declares a singleton OkHttpClient bean used ONLY by UltraMsgService.
// ─────────────────────────────────────────────────────────────────────────────

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {

    /**
     * Shared HTTP client for all UltraMsg API calls.
     * Timeouts: 10s connect, 30s read/write.
     * Tip: change Level.BASIC → Level.BODY for full request/response debug logs.
     */
    @Bean
    public OkHttpClient okHttpClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .retryOnConnectionFailure(true)
                .build();
    }
}
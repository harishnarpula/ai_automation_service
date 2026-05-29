package com.aiautomationservice.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CRITICAL FIX for loca.lt tunnel.
 *
 * loca.lt shows a passphrase page before allowing external access.
 * UltraMsg webhook calls get blocked by this page — they never reach Spring Boot.
 *
 * This filter adds the bypass header on all responses so loca.lt
 * lets requests through without the passphrase wall.
 */
@Component
public class LocalTunnelBypassFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpServletRequest httpRequest   = (HttpServletRequest) request;

        // loca.lt bypass — allows external services like UltraMsg to call without passphrase
        httpResponse.setHeader("Bypass-Tunnel-Reminder", "true");

        chain.doFilter(request, response);
    }
}
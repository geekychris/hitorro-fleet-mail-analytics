/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS policy for {@code /api/**}. Value from
 * {@code mailanalytics.cors.allowed-origins}:
 * {@code "*"} (dev) allows any origin; empty locks to same-origin;
 * comma-separated list is an explicit allow-list.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String origins;

    public CorsConfig(MailAnalyticsProperties props) {
        String o = props.getCors().getAllowedOrigins();
        this.origins = o == null ? "" : o.trim();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (origins.isEmpty()) return;
        registry.addMapping("/api/**")
                .allowedOrigins(origins.split("\\s*,\\s*"))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}

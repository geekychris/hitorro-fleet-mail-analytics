/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.config;

import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON codecs for the service's WebClients, on Spring Boot's auto-configured Jackson 3
 * {@link JsonMapper} (configured for Jackson 2's format, see {@link Jackson2CompatConfig} and
 * application.yml). The clients are built with {@link WebClient#builder()}, which on its own would
 * use a default Jackson 3 mapper; this hands them Boot's. (Replaces the 3.x {@code Jackson2Codecs}.)
 */
@Component
public class JsonCodecs {

    private final JsonMapper mapper;

    public JsonCodecs(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /** Replaces the default JSON encoder and decoder with ones on Boot's mapper. */
    public void register(ClientCodecConfigurer codecs) {
        codecs.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
        codecs.defaultCodecs().jacksonJsonEncoder(new JacksonJsonEncoder(mapper));
    }

    /** {@link WebClient#builder()} with Boot's JSON codecs. */
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder().codecs(this::register);
    }
}

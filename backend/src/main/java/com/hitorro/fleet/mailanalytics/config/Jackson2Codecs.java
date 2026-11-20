/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.config;

import org.springframework.http.codec.ClientCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Jackson 2 JSON codecs for the service's WebClients.
 *
 * <p>Spring Framework 7 picks Jackson 3 ({@code tools.jackson}) for the default
 * JSON codecs whenever it is on the classpath, and Spring Boot 4 always puts it
 * there. This service (and the HiTorro libraries) work with Jackson 2
 * {@code com.fasterxml.jackson.databind.JsonNode}, which Jackson 3 cannot read
 * or write. {@code spring.http.codecs.preferred-json-mapper=jackson2} only
 * reaches Boot's auto-configured {@code WebClient.Builder}; the clients here are
 * built with {@link WebClient#builder()}, so they register the codecs
 * themselves. The no-argument Jackson 2 codecs use the same defaults the
 * Framework applied before Spring Boot 4.
 */
@SuppressWarnings("removal")
public final class Jackson2Codecs {

    private Jackson2Codecs() {}

    /** Replaces the default JSON encoder and decoder with Jackson 2 ones. */
    public static void register(ClientCodecConfigurer codecs) {
        codecs.defaultCodecs().jacksonJsonDecoder(new Jackson2JsonDecoder());
        codecs.defaultCodecs().jacksonJsonEncoder(new Jackson2JsonEncoder());
    }

    /** {@link WebClient#builder()} with Jackson 2 JSON codecs. */
    public static WebClient.Builder webClientBuilder() {
        return WebClient.builder().codecs(Jackson2Codecs::register);
    }
}

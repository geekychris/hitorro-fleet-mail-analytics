/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient pointing at hitorro-fleet-retrieval. Single instance, Netty
 * pooling. Snug timeout because the coordinator is expected co-located.
 */
@Configuration
public class FleetRetrievalConfig {

    /** Named {@code fleetWebClient} so RetrievalClient can pick it up by name
     *  even after we later add other WebClient beans for webhook delivery. */
    @Bean
    public WebClient fleetWebClient(MailAnalyticsProperties props) {
        int timeoutMs = props.getRetrieval().getTimeoutMs();
        String baseUrl = props.getRetrieval().getBaseUrl().replaceAll("/+$", "");
        HttpClient http = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.min(5000, timeoutMs))
                .doOnConnected(c -> c.addHandlerLast(
                        new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                // Enriched mail JVS (segmented_ner per sentence) can push
                // past the 256 KiB Reactor default; 4 MiB is comfortable.
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }
}

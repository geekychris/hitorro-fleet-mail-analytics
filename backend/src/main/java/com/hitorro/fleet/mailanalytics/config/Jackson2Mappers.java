/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.config;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain mappers with Jackson 2's defaults, for the code that used {@code new ObjectMapper()} on
 * Jackson 2 (stored JSON columns, alert payloads, webhook bodies). Same settings as hitorro-core's
 * {@code JsonMappers}, which this app doesn't depend on: Jackson 2 mapper and factory defaults and
 * Jackson 2.18's stream limits (Jackson 3's are lower: nesting 500).
 */
public final class Jackson2Mappers {

    public static final StreamReadConstraints READ_CONSTRAINTS = StreamReadConstraints.builder()
            .maxNestingDepth(1000).maxStringLength(20_000_000).maxNumberLength(1000).maxNameLength(50_000)
            .maxDocumentLength(-1L).maxTokenCount(-1L).build();
    public static final StreamWriteConstraints WRITE_CONSTRAINTS = StreamWriteConstraints.builder()
            .maxNestingDepth(1000).build();

    private static final JsonMapper JSON = builder().build();

    private Jackson2Mappers() {}

    public static JsonMapper.Builder builder() {
        return JsonMapper.builder(JsonFactory.builderWithJackson2Defaults()
                .streamReadConstraints(READ_CONSTRAINTS).streamWriteConstraints(WRITE_CONSTRAINTS).build())
                .configureForJackson2();
    }

    public static JsonMapper json() {
        return JSON;
    }
}

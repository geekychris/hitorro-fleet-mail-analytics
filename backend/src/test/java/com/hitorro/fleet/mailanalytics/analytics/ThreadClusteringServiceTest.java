/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThreadClusteringServiceTest {

    @Test
    void normalizes_re_fwd_and_bracketed_list_prefixes() throws Exception {
        MailAnalyticsProperties props = new MailAnalyticsProperties();
        RetrievalClient client = mock(RetrievalClient.class);
        ObjectMapper mapper = new ObjectMapper();
        String payload = """
            {"documents":[
                {"title":{"mls":[{"text":"Re: [list] status meeting"}]},"sender_address":"a@x","times":{"date_received":1}},
                {"title":{"mls":[{"text":"Status meeting"}]},"sender_address":"b@x","times":{"date_received":2}},
                {"title":{"mls":[{"text":"Fwd: status MEETING"}]},"sender_address":"c@x","times":{"date_received":3}},
                {"title":{"mls":[{"text":"Different subject"}]},"sender_address":"d@x","times":{"date_received":4}}
            ]}""";
        when(client.execute(any())).thenReturn(mapper.readTree(payload));

        ThreadClusteringService svc = new ThreadClusteringService(client, props);
        List<ThreadClusteringService.Cluster> clusters = svc.clusters(null, null, 100);
        assertThat(clusters).hasSize(2);
        assertThat(clusters.get(0).messageCount()).isEqualTo(3);
        assertThat(clusters.get(0).key()).isEqualTo("status meeting");
    }
}

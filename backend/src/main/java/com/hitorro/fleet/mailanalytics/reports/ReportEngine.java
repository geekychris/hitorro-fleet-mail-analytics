/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.reports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.analytics.DashboardService;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.entities.Report;
import com.hitorro.fleet.mailanalytics.entities.ReportRun;
import com.hitorro.fleet.mailanalytics.entities.ReportRunStatus;
import com.hitorro.fleet.mailanalytics.query.SavedQueryService;
import com.hitorro.fleet.mailanalytics.repo.ReportRepository;
import com.hitorro.fleet.mailanalytics.repo.ReportRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReportEngine {

    private static final Logger log = LoggerFactory.getLogger(ReportEngine.class);

    private final ReportRepository reports;
    private final ReportRunRepository runs;
    private final DashboardService dashboard;
    private final SavedQueryService savedQueries;
    private final MailAnalyticsProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReportEngine(ReportRepository reports, ReportRunRepository runs,
                        DashboardService dashboard, SavedQueryService savedQueries,
                        MailAnalyticsProperties props) {
        this.reports = reports;
        this.runs = runs;
        this.dashboard = dashboard;
        this.savedQueries = savedQueries;
        this.props = props;
    }

    @Transactional
    public ReportRun runNow(Long reportId) {
        Report r = reports.findById(reportId).orElseThrow();
        ReportRun rr = new ReportRun();
        rr.setReportId(reportId);
        rr.setStartedAt(Instant.now());
        rr.setStatus(ReportRunStatus.RUNNING);
        rr = runs.save(rr);
        try {
            Map<String, Object> payload = execute(r);
            Path artifact = writeArtifact(r, rr, payload);
            rr.setArtifactPath(artifact.toString());
            rr.setStatus(ReportRunStatus.SUCCESS);
            rr.setFinishedAt(Instant.now());
            return runs.save(rr);
        } catch (Exception e) {
            log.warn("report[{}] run failed: {}", reportId, e.getMessage());
            rr.setStatus(ReportRunStatus.FAILED);
            rr.setFinishedAt(Instant.now());
            rr.setErrorMessage(e.getMessage());
            return runs.save(rr);
        }
    }

    private Map<String, Object> execute(Report r) throws Exception {
        JsonNode cfg = mapper.readTree(r.getConfigJson() == null ? "{}" : r.getConfigJson());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", Map.of("id", r.getId(), "name", r.getName(), "kind", r.getKind()));
        out.put("generatedAt", Instant.now());
        switch (r.getKind()) {
            case DASHBOARD_SNAPSHOT -> out.put("dashboard", dashboard.overview(null, null));
            case SAVED_QUERY_SET -> {
                Map<String, Object> queries = new LinkedHashMap<>();
                JsonNode ids = cfg.path("savedQueryIds");
                if (ids.isArray()) {
                    for (JsonNode id : ids) queries.put(id.asText(), savedQueries.run(id.asLong()));
                }
                out.put("queries", queries);
            }
            case CUSTOM -> out.put("config", cfg);
        }
        return out;
    }

    private Path writeArtifact(Report r, ReportRun rr, Map<String, Object> payload) throws IOException {
        Path dir = Path.of(props.getPipelines().getHome(), "reports",
                String.valueOf(r.getId()), LocalDate.now().toString());
        Files.createDirectories(dir);
        Path out = dir.resolve("run-" + rr.getId() + ".json");
        Files.writeString(out, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        return out;
    }
}

/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.entities.QueryAudit;
import com.hitorro.fleet.mailanalytics.repo.QueryAuditRepository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Servlet filter: records one QueryAudit row per /api/ call with path,
 *  request query params, and latency. Fuels the enrichment suggester. */
@Configuration
public class QueryAuditRecorder {

    @Bean
    public FilterRegistrationBean<AuditFilter> auditFilter(QueryAuditRepository repo, ObjectMapper mapper) {
        FilterRegistrationBean<AuditFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new AuditFilter(repo, mapper));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(50);
        return reg;
    }

    public static class AuditFilter implements Filter {
        private final QueryAuditRepository repo;
        private final ObjectMapper mapper;
        public AuditFilter(QueryAuditRepository repo, ObjectMapper mapper) { this.repo = repo; this.mapper = mapper; }

        @Override
        public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
                throws IOException, ServletException {
            long start = System.currentTimeMillis();
            try {
                chain.doFilter(req, resp);
            } finally {
                if (req instanceof HttpServletRequest hreq && shouldRecord(hreq)) {
                    persist(hreq, (int) (System.currentTimeMillis() - start));
                }
            }
        }

        private boolean shouldRecord(HttpServletRequest req) {
            String p = req.getRequestURI();
            // skip audit records for the audit's own reads to avoid feedback loops
            return p != null && p.startsWith("/api/") && !p.startsWith("/api/audit");
        }

        private void persist(HttpServletRequest req, int latency) {
            try {
                Map<String, Object> params = new LinkedHashMap<>();
                req.getParameterMap().forEach((k, v) -> params.put(k, v.length == 1 ? v[0] : v));
                QueryAudit q = new QueryAudit();
                q.setPath(req.getRequestURI());
                q.setQueryJson(mapper.writeValueAsString(params));
                q.setLatencyMs(latency);
                q.setAt(Instant.now());
                repo.save(q);
            } catch (Exception ignore) { /* audit failures don't break the request */ }
        }
    }
}

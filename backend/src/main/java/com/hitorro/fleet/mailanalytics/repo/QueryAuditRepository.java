/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.repo;

import com.hitorro.fleet.mailanalytics.entities.QueryAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface QueryAuditRepository extends JpaRepository<QueryAudit, Long> {
    List<QueryAudit> findByAtAfterOrderByAtDesc(Instant since);
    long deleteByAtBefore(Instant cutoff);
}

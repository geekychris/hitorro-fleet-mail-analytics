/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.repo;

import com.hitorro.fleet.mailanalytics.entities.ReportRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRunRepository extends JpaRepository<ReportRun, Long> {
    Page<ReportRun> findByReportIdOrderByStartedAtDesc(Long reportId, Pageable pageable);
}

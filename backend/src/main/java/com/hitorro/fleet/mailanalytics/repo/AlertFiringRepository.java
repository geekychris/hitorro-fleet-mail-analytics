/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.repo;

import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertFiringRepository extends JpaRepository<AlertFiring, Long> {
    Page<AlertFiring> findByAlertRuleIdOrderByFiredAtDesc(Long alertRuleId, Pageable pageable);
    Page<AlertFiring> findAllByOrderByFiredAtDesc(Pageable pageable);
}

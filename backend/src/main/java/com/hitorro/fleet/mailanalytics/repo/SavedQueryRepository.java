/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.repo;

import com.hitorro.fleet.mailanalytics.entities.SavedQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedQueryRepository extends JpaRepository<SavedQuery, Long> {
    Optional<SavedQuery> findByName(String name);
    List<SavedQuery> findAllByOrderByUpdatedAtDesc();
}

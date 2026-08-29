/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.repo;

import com.hitorro.fleet.mailanalytics.entities.EnrichmentSuggestion;
import com.hitorro.fleet.mailanalytics.entities.SuggestionKind;
import com.hitorro.fleet.mailanalytics.entities.SuggestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrichmentSuggestionRepository extends JpaRepository<EnrichmentSuggestion, Long> {
    List<EnrichmentSuggestion> findByStatusOrderByPriorityDescCreatedAtDesc(SuggestionStatus status);
    Optional<EnrichmentSuggestion> findFirstByKindAndTargetFieldAndStatus(
            SuggestionKind kind, String targetField, SuggestionStatus status);
}

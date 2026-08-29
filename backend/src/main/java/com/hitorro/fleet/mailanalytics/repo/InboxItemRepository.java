/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.repo;

import com.hitorro.fleet.mailanalytics.entities.InboxItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboxItemRepository extends JpaRepository<InboxItem, Long> {
    Page<InboxItem> findByDismissedFalseOrderByCreatedAtDesc(Pageable pageable);
    long countByReadFalseAndDismissedFalse();
}

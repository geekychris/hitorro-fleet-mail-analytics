/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.repo;

import com.hitorro.fleet.mailanalytics.entities.Watermark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatermarkRepository extends JpaRepository<Watermark, String> {
}

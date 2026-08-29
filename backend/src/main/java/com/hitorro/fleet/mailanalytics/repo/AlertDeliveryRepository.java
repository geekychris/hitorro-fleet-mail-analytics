/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.repo;

import com.hitorro.fleet.mailanalytics.entities.AlertDelivery;
import com.hitorro.fleet.mailanalytics.entities.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, Long> {
    List<AlertDelivery> findByFiringIdOrderByIdAsc(Long firingId);
    List<AlertDelivery> findByStatusIn(List<DeliveryStatus> statuses);
}

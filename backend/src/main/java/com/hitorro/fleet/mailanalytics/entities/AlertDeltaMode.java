/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.entities;

/** How an {@link AlertRule} decides whether to fire on each run. */
public enum AlertDeltaMode {
    /** Fire whenever any new doc id appears since the last run. */
    ANY_NEW,
    /** Fire when the result count crosses a numeric threshold (>= or <=). */
    COUNT_THRESHOLD,
    /** Fire when a specific aggregate value crosses a threshold. */
    VALUE_THRESHOLD,
    /** Fire on every scheduled run — no delta gating. */
    SCHEDULE_ONLY
}

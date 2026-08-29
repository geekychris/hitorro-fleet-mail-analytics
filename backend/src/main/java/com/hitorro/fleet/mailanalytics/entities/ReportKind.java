/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.entities;

public enum ReportKind {
    /** Snapshot of the dashboard overview for a time window. */
    DASHBOARD_SNAPSHOT,
    /** Run a set of saved queries and package their results. */
    SAVED_QUERY_SET,
    /** Custom config-driven report (executor picked from {@code kind}
     *  inside {@code configJson}). */
    CUSTOM
}

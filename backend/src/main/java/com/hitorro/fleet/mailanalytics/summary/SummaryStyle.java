/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.summary;

/**
 * Family of summarization treatments. Each style maps to a distinct
 * prompt template in {@link SummaryService} — same underlying model,
 * different task framing. New styles are added here and get a template
 * entry there; UI picks them up via {@code /api/summary/styles}.
 */
public enum SummaryStyle {
    /** Two-to-three sentence factual summary of the thread. */
    BRIEF,
    /** Per-participant breakdown: "X asked …, Y answered …, Z proposed …". */
    CONTRIBUTIONS,
    /** Extract action items + owners + deadlines. */
    ACTION_ITEMS,
    /** Extract decisions reached + who agreed. */
    DECISIONS,
    /** Sentiment + tone read across the thread. */
    SENTIMENT,
    /** Top-level entities (people, orgs, dates) referenced. */
    ENTITIES
}

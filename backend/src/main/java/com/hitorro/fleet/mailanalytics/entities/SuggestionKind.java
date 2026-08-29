/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.entities;

public enum SuggestionKind {
    /** User filters on a field but there's no Lucene index method for it. */
    UNINDEXED_FILTER,
    /** An NER token type appears in results often — promote to a first-class field. */
    HIGH_FREQ_NER,
    /** Body content contains URLs frequently — add a URL extractor. */
    MISSING_URL_EXTRACT,
    /** Body content contains phone numbers — add a phone extractor. */
    MISSING_PHONE_EXTRACT,
    /** Body content contains tracking numbers — add a tracking extractor. */
    MISSING_TRACKING_EXTRACT,
    /** Sender domain looks list-shaped but has no is_newsletter flag. */
    MISSING_NEWSLETTER_FLAG,
    CUSTOM
}

/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A named query the user has saved and wants to keep — feeds the search
 * UI, promotes into an {@link AlertRule} or {@link Report}. The DSL is
 * stored as JSON (see QueryBuilder) so we can evolve the schema without
 * migrations.
 */
@Entity
@Table(name = "saved_query")
public class SavedQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @Lob
    @Column(name = "dsl_json", nullable = false)
    private String dslJson;

    /** Comma-separated tags for grouping in the UI. */
    @Column(length = 1000)
    private String tags;

    @Column(length = 255)
    private String owner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDslJson() { return dslJson; }
    public void setDslJson(String dslJson) { this.dslJson = dslJson; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

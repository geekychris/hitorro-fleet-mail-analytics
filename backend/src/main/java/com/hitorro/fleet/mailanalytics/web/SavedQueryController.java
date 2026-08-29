/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.entities.SavedQuery;
import com.hitorro.fleet.mailanalytics.query.SavedQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saved-queries")
public class SavedQueryController {

    private final SavedQueryService service;

    public SavedQueryController(SavedQueryService service) { this.service = service; }

    @GetMapping
    public List<SavedQuery> list() { return service.list(); }

    @GetMapping("/{id}")
    public SavedQuery get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public SavedQuery create(@RequestBody SavedQuery q) {
        q.setId(null);
        return service.save(q);
    }

    @PutMapping("/{id}")
    public SavedQuery update(@PathVariable Long id, @RequestBody SavedQuery q) {
        q.setId(id);
        return service.save(q);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run")
    public JsonNode run(@PathVariable Long id) { return service.run(id); }
}

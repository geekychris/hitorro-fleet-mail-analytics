/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.entities.InboxItem;
import com.hitorro.fleet.mailanalytics.repo.InboxItemRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private final InboxItemRepository repo;

    public InboxController(InboxItemRepository repo) { this.repo = repo; }

    @GetMapping
    public List<InboxItem> list(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "100") int size) {
        return repo.findByDismissedFalseOrderByCreatedAtDesc(PageRequest.of(page, size)).getContent();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", repo.countByReadFalseAndDismissedFalse());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        InboxItem i = repo.findById(id).orElseThrow();
        i.setRead(true);
        repo.save(i);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable Long id) {
        InboxItem i = repo.findById(id).orElseThrow();
        i.setDismissed(true);
        repo.save(i);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/snooze")
    public ResponseEntity<InboxItem> snooze(@PathVariable Long id,
                                            @RequestParam(defaultValue = "60") long minutes) {
        InboxItem i = repo.findById(id).orElseThrow();
        i.setSnoozedUntil(Instant.now().plus(Duration.ofMinutes(minutes)));
        return ResponseEntity.ok(repo.save(i));
    }
}

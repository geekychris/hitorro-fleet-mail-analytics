/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.entities.WebhookConfig;
import com.hitorro.fleet.mailanalytics.repo.WebhookConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookConfigRepository repo;

    public WebhookController(WebhookConfigRepository repo) { this.repo = repo; }

    @GetMapping public List<WebhookConfig> list() { return repo.findAll(); }
    @GetMapping("/{id}") public WebhookConfig get(@PathVariable Long id) { return repo.findById(id).orElseThrow(); }

    @PostMapping public WebhookConfig create(@RequestBody WebhookConfig w) {
        w.setId(null);
        return repo.save(w);
    }

    @PutMapping("/{id}") public WebhookConfig update(@PathVariable Long id, @RequestBody WebhookConfig w) {
        w.setId(id);
        return repo.save(w);
    }

    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id) {
        WebhookConfig w = repo.findById(id).orElseThrow();
        try {
            Integer status = WebClient.builder().baseUrl(w.getUrl()).build()
                    .post().uri("").header("Content-Type", "application/json")
                    .bodyValue(Map.of("test", true, "name", w.getName()))
                    .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value()))
                    .block(Duration.ofSeconds(15));
            return Map.of("ok", status != null && status < 400, "status", status == null ? 0 : status);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }
}

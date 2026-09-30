package com.porganization.finance.goals;

import com.porganization.finance.goals.GoalDtos.ContributionRequest;
import com.porganization.finance.goals.GoalDtos.ContributionResponse;
import com.porganization.finance.goals.GoalDtos.GoalRequest;
import com.porganization.finance.goals.GoalDtos.GoalResponse;
import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/finance/goals")
public class GoalController {

    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    /** Metas com progresso, quanto falta e aporte mensal necessário até o prazo. */
    @GetMapping
    public List<GoalResponse> list(@CurrentUser UUID userId) {
        return service.list(userId);
    }

    @PostMapping
    public ResponseEntity<GoalResponse> create(@CurrentUser UUID userId, @Valid @RequestBody GoalRequest request) {
        GoalResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public GoalResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    @PutMapping("/{id}")
    public GoalResponse update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/contributions")
    public List<ContributionResponse> contributions(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.contributions(userId, id);
    }

    @PostMapping("/{id}/contributions")
    public ResponseEntity<ContributionResponse> contribute(@CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody ContributionRequest request) {
        return ResponseEntity.status(201).body(service.contribute(userId, id, request));
    }

    @DeleteMapping("/{id}/contributions/{contributionId}")
    public ResponseEntity<Void> deleteContribution(@CurrentUser UUID userId, @PathVariable UUID id,
            @PathVariable UUID contributionId) {
        service.deleteContribution(userId, id, contributionId);
        return ResponseEntity.noContent().build();
    }
}

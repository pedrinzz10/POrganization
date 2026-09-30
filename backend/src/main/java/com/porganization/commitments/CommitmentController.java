package com.porganization.commitments;

import com.porganization.commitments.dto.CommitmentRequest;
import com.porganization.commitments.dto.CommitmentResponse;
import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/commitments")
public class CommitmentController {

    private final CommitmentService service;

    public CommitmentController(CommitmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CommitmentResponse> create(@CurrentUser UUID userId,
            @Valid @RequestBody CommitmentRequest request) {
        Commitment created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(CommitmentResponse.from(created));
    }

    /** Compromissos de from a to (inclusive), em ordem cronológica. */
    @GetMapping
    public List<CommitmentResponse> findInRange(@CurrentUser UUID userId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return service.findInRange(userId, from, to).stream().map(CommitmentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CommitmentResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return CommitmentResponse.from(service.get(userId, id));
    }

    @PutMapping("/{id}")
    public CommitmentResponse update(@CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody CommitmentRequest request) {
        return CommitmentResponse.from(service.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}

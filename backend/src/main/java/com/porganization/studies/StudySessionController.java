package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.dto.FinishLessonRequest;
import com.porganization.studies.dto.SessionResponse;
import com.porganization.studies.dto.StartSessionRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/study/sessions")
public class StudySessionController {

    private final StudySessionService service;

    public StudySessionController(StudySessionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> start(@CurrentUser UUID userId, @Valid @RequestBody StartSessionRequest request) {
        SessionResponse started = service.start(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(started.id()).toUri();
        return ResponseEntity.created(location).body(started);
    }

    /** A sessão rodando ou pausada, para retomar o timer depois de recarregar; 204 se não houver. */
    @GetMapping("/active")
    public ResponseEntity<SessionResponse> active(@CurrentUser UUID userId) {
        return service.active(userId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/pause")
    public SessionResponse pause(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.pause(userId, id);
    }

    @PostMapping("/{id}/resume")
    public SessionResponse resume(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.resume(userId, id);
    }

    @PostMapping("/{id}/finish")
    public SessionResponse finish(@CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody FinishLessonRequest request) {
        return service.finish(userId, id, request);
    }

    @PostMapping("/{id}/abandon")
    public SessionResponse abandon(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.abandon(userId, id);
    }
}

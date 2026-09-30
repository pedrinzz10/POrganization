package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.dto.SubjectOrderRequest;
import com.porganization.studies.dto.SubjectRequest;
import com.porganization.studies.dto.SubjectResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService service;

    public SubjectController(SubjectService service) {
        this.service = service;
    }

    /** Matérias em ordem de prioridade; ?tag= filtra pelo nome da tag (sem diferenciar maiúsculas). */
    @GetMapping
    public List<SubjectResponse> list(@CurrentUser UUID userId, @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(userId, tag, includeArchived);
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> create(@CurrentUser UUID userId, @Valid @RequestBody SubjectRequest request) {
        SubjectResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public SubjectResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    @PutMapping("/{id}")
    public SubjectResponse update(@CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody SubjectRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Nova ordem de prioridade (arrastar e soltar na tela). */
    @PutMapping("/order")
    public List<SubjectResponse> reorder(@CurrentUser UUID userId, @Valid @RequestBody SubjectOrderRequest request) {
        return service.reorder(userId, request.ids());
    }
}

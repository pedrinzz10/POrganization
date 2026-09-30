package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.dto.TagRequest;
import com.porganization.studies.dto.TagResponse;
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
@RequestMapping("/api/tags")
public class TagController {

    private final SubjectService service;

    public TagController(SubjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<TagResponse> list(@CurrentUser UUID userId) {
        return service.listTags(userId);
    }

    /** Nome repetido (sem diferenciar maiúsculas) responde 409. */
    @PostMapping
    public ResponseEntity<TagResponse> create(@CurrentUser UUID userId, @Valid @RequestBody TagRequest request) {
        TagResponse created = service.createTag(userId, request.name());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public TagResponse rename(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody TagRequest request) {
        return service.renameTag(userId, id, request.name());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.deleteTag(userId, id);
        return ResponseEntity.noContent().build();
    }
}

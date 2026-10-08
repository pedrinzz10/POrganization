package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.PlannedLessonService.PlannedLesson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aulas definidas de uma matéria, na ordem do curso (E14). */
@RestController
@RequestMapping("/api/subjects/{subjectId}/planned-lessons")
public class PlannedLessonController {

    private final PlannedLessonService service;

    public PlannedLessonController(PlannedLessonService service) {
        this.service = service;
    }

    public record AddRequest(@NotEmpty @Size(max = PlannedLessonService.MAX_LESSONS) List<@NotNull String> titles) {
    }

    public record RenameRequest(@NotBlank @Size(max = 200) String title) {
    }

    public record OrderRequest(@NotNull List<@NotNull UUID> ids) {
    }

    @GetMapping
    public List<PlannedLesson> list(@CurrentUser UUID userId, @PathVariable UUID subjectId) {
        return service.list(userId, subjectId);
    }

    /** Inclui no fim, na ordem enviada; devolve a lista inteira. */
    @PostMapping
    public List<PlannedLesson> add(@CurrentUser UUID userId, @PathVariable UUID subjectId, @Valid @RequestBody AddRequest request) {
        return service.add(userId, subjectId, request.titles());
    }

    @PatchMapping("/{id}")
    public PlannedLesson rename(@CurrentUser UUID userId, @PathVariable UUID subjectId, @PathVariable UUID id,
            @Valid @RequestBody RenameRequest request) {
        return service.rename(userId, subjectId, id, request.title());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID subjectId, @PathVariable UUID id) {
        service.delete(userId, subjectId, id);
        return ResponseEntity.noContent().build();
    }

    /** Nova sequência (arrastar na lista). */
    @PutMapping("/order")
    public List<PlannedLesson> reorder(@CurrentUser UUID userId, @PathVariable UUID subjectId,
            @Valid @RequestBody OrderRequest request) {
        return service.reorder(userId, subjectId, request.ids());
    }
}

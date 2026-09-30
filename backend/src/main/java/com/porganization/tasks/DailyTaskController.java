package com.porganization.tasks;

import com.porganization.security.CurrentUser;
import com.porganization.tasks.DailyTaskDtos.ArchivePatch;
import com.porganization.tasks.DailyTaskDtos.DayTask;
import com.porganization.tasks.DailyTaskDtos.OrderRequest;
import com.porganization.tasks.DailyTaskDtos.TaskRequest;
import com.porganization.tasks.DailyTaskDtos.TaskResponse;
import com.porganization.tasks.DailyTaskDtos.TaskStatsResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tasks")
public class DailyTaskController {

    private final DailyTaskService service;

    public DailyTaskController(DailyTaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskResponse> list(@CurrentUser UUID userId) {
        return service.list(userId);
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@CurrentUser UUID userId, @Valid @RequestBody TaskRequest request) {
        TaskResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Tarefas devidas no dia (padrão: hoje no fuso do usuário), com feito ou não. */
    @GetMapping("/day")
    public List<DayTask> day(@CurrentUser UUID userId, @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return service.day(userId, date != null ? date : service.today(userId));
    }

    /** Sequência atual e % de conclusão dos últimos 30 dias, por tarefa. */
    @GetMapping("/stats")
    public List<TaskStatsResponse> stats(@CurrentUser UUID userId) {
        return service.stats(userId);
    }

    @PutMapping("/order")
    public List<TaskResponse> reorder(@CurrentUser UUID userId, @Valid @RequestBody OrderRequest request) {
        return service.reorder(userId, request.ids());
    }

    @GetMapping("/{id}")
    public TaskResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody TaskRequest request) {
        return service.update(userId, id, request);
    }

    @PatchMapping("/{id}")
    public TaskResponse archive(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody ArchivePatch patch) {
        return service.setArchived(userId, id, patch.archived());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/completions/{date}")
    public ResponseEntity<Void> complete(@CurrentUser UUID userId, @PathVariable UUID id,
            @PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        service.complete(userId, id, date);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/completions/{date}")
    public ResponseEntity<Void> uncomplete(@CurrentUser UUID userId, @PathVariable UUID id,
            @PathVariable @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        service.uncomplete(userId, id, date);
        return ResponseEntity.noContent().build();
    }
}

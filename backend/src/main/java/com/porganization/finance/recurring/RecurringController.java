package com.porganization.finance.recurring;

import com.porganization.finance.recurring.RecurringDtos.GenerateResponse;
import com.porganization.finance.recurring.RecurringDtos.RecurringRequest;
import com.porganization.finance.recurring.RecurringDtos.RecurringResponse;
import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.YearMonth;
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
@RequestMapping("/api/finance/recurring")
public class RecurringController {

    private final RecurringService service;
    private final RecurringGenerator generator;

    public RecurringController(RecurringService service, RecurringGenerator generator) {
        this.service = service;
        this.generator = generator;
    }

    @GetMapping
    public List<RecurringResponse> list(@CurrentUser UUID userId) {
        return service.list(userId);
    }

    @PostMapping
    public ResponseEntity<RecurringResponse> create(@CurrentUser UUID userId, @Valid @RequestBody RecurringRequest request) {
        RecurringResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public RecurringResponse update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody RecurringRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Gera o mês agora (o mesmo que acontece ao consultar o mês em /transactions ou /summary). */
    @PostMapping("/generate")
    public GenerateResponse generate(@CurrentUser UUID userId, @RequestParam YearMonth month) {
        return new GenerateResponse(month, generator.generate(userId, month));
    }
}

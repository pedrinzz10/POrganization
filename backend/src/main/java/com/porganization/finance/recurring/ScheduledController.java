package com.porganization.finance.recurring;

import com.porganization.finance.recurring.ScheduledDtos.ConfirmRequest;
import com.porganization.finance.recurring.ScheduledDtos.Occurrence;
import com.porganization.finance.recurring.ScheduledDtos.RescheduleRequest;
import com.porganization.finance.recurring.ScheduledDtos.RescheduleResponse;
import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ocorrências dos agendados em conta: a lista do mês e as ações em cada uma. */
@RestController
@RequestMapping("/api/finance/scheduled")
public class ScheduledController {

    private final ScheduledService service;

    public ScheduledController(ScheduledService service) {
        this.service = service;
    }

    @GetMapping
    public List<Occurrence> month(@CurrentUser UUID userId, @RequestParam YearMonth month) {
        return service.month(userId, month);
    }

    /** "Recebi"/"Paguei". Corpo opcional: {amount, date}. */
    @PostMapping("/{id}/confirm")
    public Occurrence confirm(@CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody(required = false) ConfirmRequest request) {
        return service.confirm(userId, id, request == null ? new ConfirmRequest(null, null) : request);
    }

    @PostMapping("/{id}/reschedule")
    public RescheduleResponse reschedule(@CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody RescheduleRequest request) {
        return service.reschedule(userId, id, request.date());
    }

    /** "Não vou receber/pagar este mês". */
    @PostMapping("/{id}/skip")
    public ResponseEntity<Void> skip(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.skip(userId, id);
        return ResponseEntity.noContent().build();
    }
}

package com.porganization.finance.transactions;

import com.porganization.finance.transactions.TransactionDtos.MonthSummary;
import com.porganization.finance.transactions.TransactionDtos.TransactionRequest;
import com.porganization.finance.transactions.TransactionDtos.TransactionResponse;
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
@RequestMapping("/api/finance")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    /** Filtros combináveis: ?month=YYYY-MM&accountId&categoryId&tagId&type. */
    @GetMapping("/transactions")
    public List<TransactionResponse> list(@CurrentUser UUID userId,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) TransactionType type) {
        return service.list(userId, month, accountId, categoryId, tagId, type);
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> create(@CurrentUser UUID userId, @Valid @RequestBody TransactionRequest request) {
        TransactionResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/transactions/{id}")
    public TransactionResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    @PutMapping("/transactions/{id}")
    public TransactionResponse update(@CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Renda x gasto do mês. */
    @GetMapping("/summary")
    public MonthSummary summary(@CurrentUser UUID userId, @RequestParam YearMonth month) {
        return service.summary(userId, month);
    }
}

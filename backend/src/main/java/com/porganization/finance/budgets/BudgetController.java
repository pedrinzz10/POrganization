package com.porganization.finance.budgets;

import com.porganization.finance.budgets.BudgetDtos.BudgetAmountRequest;
import com.porganization.finance.budgets.BudgetDtos.BudgetRequest;
import com.porganization.finance.budgets.BudgetDtos.BudgetStatus;
import com.porganization.finance.recurring.RecurringGenerator;
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
@RequestMapping("/api/finance/budgets")
public class BudgetController {

    private final BudgetService service;
    private final RecurringGenerator recurring;

    public BudgetController(BudgetService service, RecurringGenerator recurring) {
        this.service = service;
        this.recurring = recurring;
    }

    /** Orçamentos do mês com gasto realizado, percentual e nível (OK, ATENCAO, ESTOURADO). */
    @GetMapping
    public List<BudgetStatus> list(@CurrentUser UUID userId, @RequestParam YearMonth month) {
        // Os fixos do mês contam no gasto, então o mês é gerado antes (idempotente)
        recurring.generate(userId, month);
        return service.status(userId, month);
    }

    @PostMapping
    public ResponseEntity<BudgetStatus> create(@CurrentUser UUID userId, @Valid @RequestBody BudgetRequest request) {
        BudgetStatus created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public BudgetStatus update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody BudgetAmountRequest request) {
        return service.updateAmount(userId, id, request.amount());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}

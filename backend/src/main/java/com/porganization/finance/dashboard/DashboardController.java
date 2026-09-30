package com.porganization.finance.dashboard;

import com.porganization.finance.recurring.RecurringGenerator;
import com.porganization.security.CurrentUser;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/dashboard")
public class DashboardController {

    private final DashboardService service;
    private final RecurringGenerator recurring;

    public DashboardController(DashboardService service, RecurringGenerator recurring) {
        this.service = service;
        this.recurring = recurring;
    }

    /** Resumo do mês: saldos, renda x gasto, categorias, faturas, orçamentos em alerta, metas e 6 meses. */
    @GetMapping
    public DashboardResponse dashboard(@CurrentUser UUID userId, @RequestParam YearMonth month) {
        recurring.generate(userId, month);
        return service.of(userId, month);
    }
}

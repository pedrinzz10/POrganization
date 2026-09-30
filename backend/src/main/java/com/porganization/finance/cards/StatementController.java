package com.porganization.finance.cards;

import com.porganization.finance.cards.CardDtos.StatementResponse;
import com.porganization.security.CurrentUser;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/cards")
public class StatementController {

    private final StatementService service;

    public StatementController(StatementService service) {
        this.service = service;
    }

    /** Fatura do mês (mês do vencimento) com itens, total, status e limite disponível. Sem ?month, a lista (CreditCardController). */
    @GetMapping(value = "/{id}/statements", params = "month")
    public StatementResponse ofMonth(@CurrentUser UUID userId, @PathVariable UUID id, @RequestParam YearMonth month) {
        return service.ofMonth(userId, id, month);
    }

    @PostMapping("/statements/{id}/pay")
    public StatementResponse pay(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.pay(userId, id);
    }
}

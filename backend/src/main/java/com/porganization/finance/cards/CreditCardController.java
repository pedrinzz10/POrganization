package com.porganization.finance.cards;

import com.porganization.finance.cards.CardDtos.CardRequest;
import com.porganization.finance.cards.CardDtos.CardResponse;
import com.porganization.finance.cards.CardDtos.PurchaseRequest;
import com.porganization.finance.cards.CardDtos.StatementSummary;
import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/finance/cards")
public class CreditCardController {

    private final CreditCardService service;

    public CreditCardController(CreditCardService service) {
        this.service = service;
    }

    @GetMapping
    public List<CardResponse> list(@CurrentUser UUID userId) {
        return service.list(userId);
    }

    @PostMapping
    public ResponseEntity<CardResponse> create(@CurrentUser UUID userId, @Valid @RequestBody CardRequest request) {
        CardResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public CardResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    @PutMapping("/{id}")
    public CardResponse update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody CardRequest request) {
        return service.update(userId, id, request);
    }

    /** Lançar uma compra no cartão: cai na fatura certa pela data. */
    @PostMapping("/{id}/purchases")
    public ResponseEntity<Map<String, UUID>> purchase(@CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody PurchaseRequest request) {
        UUID created = service.purchase(userId, id, request);
        return ResponseEntity.status(201).body(Map.of("id", created));
    }

    @GetMapping("/{id}/statements")
    public List<StatementSummary> statements(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.statements(userId, id);
    }
}

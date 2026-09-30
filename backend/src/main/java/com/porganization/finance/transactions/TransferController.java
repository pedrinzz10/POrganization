package com.porganization.finance.transactions;

import com.porganization.finance.transactions.TransactionDtos.TransferRequest;
import com.porganization.finance.transactions.TransactionDtos.TransferResponse;
import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/finance/transfers")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@CurrentUser UUID userId, @Valid @RequestBody TransferRequest request) {
        TransferResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.groupId()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{groupId}")
    public TransferResponse update(@CurrentUser UUID userId, @PathVariable UUID groupId,
            @Valid @RequestBody TransferRequest request) {
        return service.update(userId, groupId, request);
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID groupId) {
        service.delete(userId, groupId);
        return ResponseEntity.noContent().build();
    }
}

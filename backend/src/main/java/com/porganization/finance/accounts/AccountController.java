package com.porganization.finance.accounts;

import com.porganization.finance.accounts.AccountDtos.AccountRequest;
import com.porganization.finance.accounts.AccountDtos.AccountResponse;
import com.porganization.finance.accounts.AccountDtos.ArchivePatch;
import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/finance/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountResponse> list(@CurrentUser UUID userId, @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(userId, includeArchived);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@CurrentUser UUID userId, @Valid @RequestBody AccountRequest request) {
        AccountResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return service.get(userId, id);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        return service.update(userId, id, request);
    }

    /** Arquivar ou desarquivar: a conta sai das listas, mas o histórico continua. */
    @PatchMapping("/{id}")
    public AccountResponse archive(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody ArchivePatch patch) {
        return service.setArchived(userId, id, patch.archived());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}

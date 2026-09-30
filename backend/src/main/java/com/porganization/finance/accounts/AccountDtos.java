package com.porganization.finance.accounts;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** DTOs da API de contas. Valores em BigDecimal, que trafegam como string ("1000.10"). */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull AccountType type,
            @NotNull @Digits(integer = 12, fraction = 2) BigDecimal initialBalance) {
    }

    public record ArchivePatch(@NotNull Boolean archived) {
    }

    /** balance = saldo inicial + transações pagas (as transações chegam na F03). */
    public record AccountResponse(UUID id, String name, AccountType type, BigDecimal initialBalance, BigDecimal balance,
            boolean archived) {
    }
}

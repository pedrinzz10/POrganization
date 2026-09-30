package com.porganization.finance.categories;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull CategoryKind kind,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "use o formato #RRGGBB") String color,
            @Size(max = 60) String icon) {
    }

    public record CategoryResponse(UUID id, String name, CategoryKind kind, String color, String icon) {

        static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getKind(), c.getColor(), c.getIcon());
        }
    }

    public record FinanceTagRequest(@NotBlank @Size(max = 50) String name) {
    }

    public record FinanceTagResponse(UUID id, String name) {

        static FinanceTagResponse from(FinanceTag t) {
            return new FinanceTagResponse(t.getId(), t.getName());
        }
    }
}

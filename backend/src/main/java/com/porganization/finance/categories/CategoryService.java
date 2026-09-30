package com.porganization.finance.categories;

import com.porganization.common.ConflictException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.categories.CategoryDtos.CategoryRequest;
import com.porganization.finance.categories.CategoryDtos.CategoryResponse;
import com.porganization.finance.categories.CategoryDtos.FinanceTagResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    /** Categorias criadas no primeiro acesso: nome, tipo, cor e ícone (Material Symbols). */
    private record Default(String name, CategoryKind kind, String color, String icon) {
    }

    private static final List<Default> DEFAULTS = List.of(
            new Default("Alimentação", CategoryKind.EXPENSE, "#F57C00", "restaurant"),
            new Default("Transporte", CategoryKind.EXPENSE, "#1976D2", "directions_bus"),
            new Default("Moradia", CategoryKind.EXPENSE, "#6D4C41", "home"),
            new Default("Lazer", CategoryKind.EXPENSE, "#8E24AA", "celebration"),
            new Default("Saúde", CategoryKind.EXPENSE, "#E53935", "favorite"),
            new Default("Educação", CategoryKind.EXPENSE, "#3949AB", "school"),
            new Default("Salário", CategoryKind.INCOME, "#43A047", "payments"),
            new Default("Outros", CategoryKind.EXPENSE, "#757575", "category"));

    private final CategoryRepository categories;
    private final FinanceTagRepository tags;

    public CategoryService(CategoryRepository categories, FinanceTagRepository tags) {
        this.categories = categories;
        this.tags = tags;
    }

    /**
     * No primeiro acesso o usuário recebe as categorias padrão, uma única vez: a marca em
     * finance_setup é gravada com "on conflict do nothing", então dois acessos simultâneos não
     * duplicam, e apagar as categorias depois não as faz voltar.
     */
    @Transactional
    public List<CategoryResponse> list(UUID userId) {
        if (categories.markSeeded(userId) == 1) {
            DEFAULTS.forEach(d -> categories.save(new Category(userId, d.name(), d.kind(), d.color(), d.icon())));
        }
        return categories.findByUserIdOrderByKindAscNameAsc(userId).stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        String name = request.name().trim();
        ensureUnique(userId, request.kind(), name, null);
        return CategoryResponse.from(categories.save(new Category(userId, name, request.kind(), request.color(), request.icon())));
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID id, CategoryRequest request) {
        Category category = find(userId, id);
        String name = request.name().trim();
        ensureUnique(userId, request.kind(), name, id);
        category.setName(name);
        category.setKind(request.kind());
        category.setColor(request.color());
        category.setIcon(request.icon());
        return CategoryResponse.from(category);
    }

    /** Categoria em uso não pode ser excluída (o banco barra pela FK das transações): 409. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        categories.delete(find(userId, id));
        try {
            categories.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Esta categoria tem lançamentos e não pode ser excluída.");
        }
    }

    public Category find(UUID userId, UUID id) {
        return categories.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
    }

    private void ensureUnique(UUID userId, CategoryKind kind, String name, UUID ignoreId) {
        categories.findByUserIdAndKindAndNameIgnoreCase(userId, kind, name)
                .filter(other -> !other.getId().equals(ignoreId))
                .ifPresent(other -> {
                    throw new ConflictException("Já existe uma categoria com esse nome");
                });
    }

    // ---------- tags financeiras ----------

    @Transactional(readOnly = true)
    public List<FinanceTagResponse> listTags(UUID userId) {
        return tags.findByUserIdOrderByNameAsc(userId).stream().map(FinanceTagResponse::from).toList();
    }

    @Transactional
    public FinanceTagResponse createTag(UUID userId, String name) {
        String trimmed = name.trim();
        if (tags.findByUserIdAndNameIgnoreCase(userId, trimmed).isPresent()) {
            throw new ConflictException("Já existe uma tag com esse nome");
        }
        return FinanceTagResponse.from(tags.save(new FinanceTag(userId, trimmed)));
    }

    @Transactional
    public void deleteTag(UUID userId, UUID id) {
        tags.delete(tags.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Tag não encontrada")));
    }
}

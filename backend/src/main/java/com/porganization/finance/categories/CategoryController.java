package com.porganization.finance.categories;

import com.porganization.finance.categories.CategoryDtos.CategoryRequest;
import com.porganization.finance.categories.CategoryDtos.CategoryResponse;
import com.porganization.finance.categories.CategoryDtos.FinanceTagRequest;
import com.porganization.finance.categories.CategoryDtos.FinanceTagResponse;
import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/finance")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    /** No primeiro acesso cria as categorias padrão (uma única vez). */
    @GetMapping("/categories")
    public List<CategoryResponse> list(@CurrentUser UUID userId) {
        return service.list(userId);
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> create(@CurrentUser UUID userId, @Valid @RequestBody CategoryRequest request) {
        CategoryResponse created = service.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return service.update(userId, id, request);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tags")
    public List<FinanceTagResponse> listTags(@CurrentUser UUID userId) {
        return service.listTags(userId);
    }

    @PostMapping("/tags")
    public ResponseEntity<FinanceTagResponse> createTag(@CurrentUser UUID userId, @Valid @RequestBody FinanceTagRequest request) {
        FinanceTagResponse created = service.createTag(userId, request.name());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> deleteTag(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.deleteTag(userId, id);
        return ResponseEntity.noContent().build();
    }
}

package com.porganization.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints que só existem nos testes, para o GlobalExceptionHandlerTest provocar cada erro.
 * Fica em arquivo próprio porque o component scan dos testes ignora classes aninhadas em testes.
 */
@RestController
public class ErrosDeTesteController {

    public static final String BASE = "/api/_teste/erros";

    public record PedidoDeTeste(@NotBlank String titulo, @Positive int quantidade) {
    }

    @PostMapping(BASE + "/validacao")
    PedidoDeTeste validacao(@Valid @RequestBody PedidoDeTeste pedido) {
        return pedido;
    }

    @GetMapping(BASE + "/nao-encontrado")
    void naoEncontrado() {
        throw new NotFoundException("Compromisso não encontrado");
    }

    @GetMapping(BASE + "/inesperado")
    void inesperado() {
        throw new RuntimeException("segredo");
    }
}

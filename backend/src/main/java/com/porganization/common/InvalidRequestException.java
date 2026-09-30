package com.porganization.common;

/**
 * Pedido que passou na validação dos campos, mas quebra uma regra que envolve o contexto
 * (ex.: fim antes do início). Responde 400 com o campo em errors[{field, message}].
 */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

package com.porganization.common;

/** O pedido conflita com o estado atual (ex.: nome já usado, item já pago). Responde 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

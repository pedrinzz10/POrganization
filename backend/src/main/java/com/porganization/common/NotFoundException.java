package com.porganization.common;

/**
 * Registro inexistente ou de outro usuário. Os dois casos respondem 404 de propósito:
 * a API não revela se um id existe para outra pessoa.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

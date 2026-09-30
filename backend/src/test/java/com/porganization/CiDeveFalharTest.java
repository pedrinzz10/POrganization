package com.porganization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// B10 T3 (CA3): commit temporário para provar que teste quebrado deixa o CI vermelho
class CiDeveFalharTest {

    @Test
    void falhaDePropósito() {
        assertEquals(1, 2);
    }
}

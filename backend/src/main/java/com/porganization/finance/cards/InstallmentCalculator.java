package com.porganization.finance.cards;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Divide o valor de uma compra em parcelas, em centavos inteiros: todas iguais e o resto da
 * divisão na primeira (100.00 em 3x = 33.34 + 33.33 + 33.33). A soma é sempre exatamente o total.
 */
public final class InstallmentCalculator {

    public static final int MAX_INSTALLMENTS = 48;

    private InstallmentCalculator() {
    }

    public static List<BigDecimal> split(BigDecimal total, int installments) {
        if (installments < 1 || installments > MAX_INSTALLMENTS) {
            throw new IllegalArgumentException("parcelas devem estar entre 1 e " + MAX_INSTALLMENTS);
        }
        long cents = total.movePointRight(2).longValueExact();
        if (cents < installments) {
            throw new IllegalArgumentException("valor pequeno demais para " + installments + " parcelas");
        }
        long base = cents / installments;
        long remainder = cents - base * installments;

        List<BigDecimal> values = new ArrayList<>(installments);
        values.add(BigDecimal.valueOf(base + remainder, 2));
        for (int i = 1; i < installments; i++) {
            values.add(BigDecimal.valueOf(base, 2));
        }
        return values;
    }
}

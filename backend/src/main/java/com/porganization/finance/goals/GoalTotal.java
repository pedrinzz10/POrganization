package com.porganization.finance.goals;

import java.math.BigDecimal;
import java.util.UUID;

/** Total guardado numa meta (soma dos aportes). */
public record GoalTotal(UUID goalId, BigDecimal total) {
}

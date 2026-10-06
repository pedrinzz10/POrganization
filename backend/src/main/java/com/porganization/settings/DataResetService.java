package com.porganization.settings;

import com.porganization.common.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga todos os dados do usuário numa seção do app, de uma vez (B14). As tabelas filhas que
 * têm "on delete cascade" saem junto com a mãe; a ordem da lista respeita as FKs "restrict".
 * Preferências, notificações e a conexão com o Google ficam; eventos já publicados no
 * Google Calendar também (o app não apaga nada lá).
 */
@Service
public class DataResetService {

    private static final Map<String, List<String>> TABLES = Map.of(
            // lembretes, exceções e log de notificação saem em cascata
            "commitments", List.of("commitments"),
            // dias da semana e marcações saem em cascata
            "tasks", List.of("daily_tasks"),
            // aulas, sessões, revisões, tags da matéria e aulas fixadas saem em cascata
            "studies", List.of("subjects", "tags"),
            // finance_setup sai para as categorias padrão voltarem no próximo acesso
            "finance", List.of("transactions", "recurring_transactions", "budgets", "savings_goals",
                    "card_statements", "credit_cards", "accounts", "categories", "finance_tags", "finance_setup"));

    private final JdbcTemplate jdbc;

    public DataResetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void reset(UUID userId, String section) {
        List<String> tables = TABLES.get(section);
        if (tables == null) {
            throw new NotFoundException("Seção desconhecida: " + section);
        }
        // Nomes vêm da lista fixa acima, nunca da requisição
        tables.forEach(table -> jdbc.update("delete from " + table + " where user_id = ?", userId));
    }
}

package com.porganization.notifications;

import java.util.List;
import java.util.UUID;

/**
 * Um aviso pronto para qualquer canal: o e-mail usa subject como assunto e lines como parágrafos;
 * o push usa subject como título e a primeira linha como texto. url abre o app no lugar certo.
 */
public record Notification(UUID userId, Kind kind, String subject, List<String> lines, String url) {

    public enum Kind {
        REMINDER,
        DAILY_DIGEST
    }
}

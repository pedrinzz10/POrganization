package com.porganization.commitments;

import java.util.UUID;

/**
 * Um compromisso mudou (publicado pelo CommitmentService, dentro da transação). Quem escuta depois
 * do commit (ex.: a sincronização com o Google) recarrega o compromisso pelo id; na exclusão, o
 * googleEventId vem no evento porque a linha já não existe.
 */
public record CommitmentChangedEvent(UUID userId, UUID commitmentId, Change change, String googleEventId) {

    public enum Change {
        SAVED,
        DELETED
    }
}

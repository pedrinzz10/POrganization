package com.porganization.integrations.google;

import com.porganization.commitments.CommitmentChangedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Leva cada mudança de compromisso ao Google depois do commit e fora da thread da requisição:
 * a resposta ao usuário não espera a Calendar API, e uma falha lá não desfaz o que foi salvo.
 */
@Component
public class CommitmentSyncListener {

    private final GoogleSyncService sync;

    public CommitmentSyncListener(GoogleSyncService sync) {
        this.sync = sync;
    }

    @Async(GoogleConfig.SYNC_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommitmentChangedEvent event) {
        switch (event.change()) {
            case SAVED -> sync.sync(event.userId(), event.commitmentId());
            case DELETED -> sync.delete(event.userId(), event.googleEventId());
        }
    }
}

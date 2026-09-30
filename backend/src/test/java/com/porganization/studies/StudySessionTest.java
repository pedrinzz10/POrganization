package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StudySessionTest {

    private static Instant as(String hora) {
        return Instant.parse("2026-10-01T" + hora + ":00Z");
    }

    // E04 T2 (CA2)
    @Test
    void duracaoEfetivaDescontaAsPausas() {
        StudySession sessao = StudySession.start(UUID.randomUUID(), UUID.randomUUID(), SessionType.LESSON, as("10:00"));
        sessao.pause(as("10:20"));
        sessao.resume(as("10:25"));
        sessao.finish(as("10:55"));

        assertThat(sessao.getPausedSeconds()).isEqualTo(300);
        assertThat(sessao.effectiveMinutes()).isEqualTo(50);
        assertThat(sessao.getStatus()).isEqualTo(SessionStatus.FINISHED);
    }

    @Test
    void terminarPausadaContaAPausaAteOFim() {
        StudySession sessao = StudySession.start(UUID.randomUUID(), UUID.randomUUID(), SessionType.LESSON, as("10:00"));
        sessao.pause(as("10:30"));
        sessao.finish(as("10:40"));

        assertThat(sessao.getPausedSeconds()).isEqualTo(600);
        assertThat(sessao.effectiveMinutes()).isEqualTo(30);
    }

    @Test
    void emAndamentoCalculaAteAgora() {
        StudySession sessao = StudySession.start(UUID.randomUUID(), UUID.randomUUID(), SessionType.REVIEW, as("10:00"));
        sessao.pause(as("10:10"));

        // pausada desde 10:10: às 10:30 continua com 10 minutos efetivos
        assertThat(sessao.elapsedSeconds(as("10:30"))).isEqualTo(600);
    }

    @Test
    void minutosArredondamParaOMaisProximo() {
        StudySession sessao = StudySession.start(UUID.randomUUID(), UUID.randomUUID(), SessionType.LESSON, as("10:00"));
        sessao.finish(Instant.parse("2026-10-01T10:24:31Z"));
        assertThat(sessao.effectiveMinutes()).isEqualTo(25);
    }
}

package com.porganization.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReminderDispatcherIT extends NotificationsIT {

    private static final String SERIE_DIARIA_15H = """
            {"title":"Remédio","date":"2026-09-20","startTime":"15:00","recurrenceRule":{"freq":"DAILY","interval":1}}
            """;
    private static final String DENTISTA_15H = "{\"title\":\"Dentista\",\"date\":\"2026-10-01\",\"startTime\":\"15:00\"}";

    // 14:31 em São Paulo (UTC-3) = 17:31Z
    private static final Instant HOJE_14_31 = Instant.parse("2026-10-01T17:31:00Z");

    // I01 T1 (CA1)
    @Test
    void serieDiariaAs15ComLembreteDe30DisparaAs1431ParaAOcorrenciaDeHoje() throws Exception {
        lembrete(compromisso(SERIE_DIARIA_15H), 30, ChannelType.PUSH);
        clock.setInstant(HOJE_14_31);

        cron();

        ArgumentCaptor<Notification> enviado = ArgumentCaptor.forClass(Notification.class);
        verify(push, times(1)).send(enviado.capture());
        assertThat(enviado.getValue().subject()).isEqualTo("Lembrete: Remédio às 15:00");
        assertThat(enviado.getValue().lines().getFirst()).isEqualTo("quinta-feira, 1 de outubro às 15:00");
        assertThat(enviado.getValue().userId()).isEqualTo(userId);
        verify(email, never()).send(any());
    }

    // I01 T2 (CA2)
    @Test
    void chamarODispatchDuasVezesNaoDuplica() throws Exception {
        lembrete(compromisso(SERIE_DIARIA_15H), 30, ChannelType.PUSH);
        clock.setInstant(HOJE_14_31);

        cron();
        clock.setInstant(HOJE_14_31.plusSeconds(4 * 60));
        cron();

        verify(push, times(1)).send(any());
    }

    @Test
    void foraDaJanelaNaoDispara() throws Exception {
        lembrete(compromisso(SERIE_DIARIA_15H), 30, ChannelType.PUSH);

        // 14:15: o aviso (14:30) ainda não chegou
        clock.setInstant(Instant.parse("2026-10-01T17:15:00Z"));
        cron();
        // 14:41: passou mais de 10 minutos do aviso
        clock.setInstant(Instant.parse("2026-10-01T17:41:00Z"));
        cron();

        verify(push, never()).send(any());
    }

    @Test
    void cadaCanalDoLembreteEnviaUmaVez() throws Exception {
        lembrete(compromisso(DENTISTA_15H), 30, ChannelType.PUSH, ChannelType.EMAIL);
        clock.setInstant(HOJE_14_31);

        cron();

        verify(push, times(1)).send(any());
        verify(email, times(1)).send(any());
    }

    @Test
    void falhaNumCanalLiberaParaTentarDeNovoENaoAtrapalhaOOutro() throws Exception {
        lembrete(compromisso(DENTISTA_15H), 30, ChannelType.PUSH, ChannelType.EMAIL);
        doThrow(new IllegalStateException("SMTP fora do ar")).doNothing().when(email).send(any());
        clock.setInstant(HOJE_14_31);

        cron();
        verify(push, times(1)).send(any());

        // Na chamada seguinte (ainda na janela) o e-mail que falhou é reenviado; o push não
        clock.setInstant(HOJE_14_31.plusSeconds(5 * 60));
        cron();
        verify(email, times(2)).send(any());
        verify(push, times(1)).send(any());
    }

    @Test
    void compromissoDeDiaTodoContaComoNoveDaManha() throws Exception {
        lembrete(compromisso("{\"title\":\"Aniversário\",\"date\":\"2026-10-01\"}"), 60, ChannelType.PUSH);
        // 08:05 em São Paulo: aviso das 08:00 (09:00 − 60 min)
        clock.setInstant(Instant.parse("2026-10-01T11:05:00Z"));

        cron();

        ArgumentCaptor<Notification> enviado = ArgumentCaptor.forClass(Notification.class);
        verify(push).send(enviado.capture());
        assertThat(enviado.getValue().subject()).isEqualTo("Lembrete: Aniversário hoje");
    }
}

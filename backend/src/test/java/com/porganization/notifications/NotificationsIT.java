package com.porganization.notifications;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base dos testes de notificação: os canais de e-mail e push são mocks (mesmos nomes dos beans
 * reais), então dá para contar os envios sem SMTP nem push service. As subclasses compartilham o contexto.
 */
abstract class NotificationsIT extends IntegrationTest {

    @MockitoBean(name = "emailChannel")
    protected NotificationChannel email;

    @MockitoBean(name = "pushChannel")
    protected NotificationChannel push;

    @Autowired
    protected ReminderRepository reminders;

    protected final UUID userId = UUID.randomUUID();

    @BeforeEach
    void tiposDosCanais() {
        // O dispatcher olha os lembretes de todos os usuários: sobra de outro teste dispararia aqui
        jdbc.update("delete from reminders");
        // Idem para o resumo diário configurado em outros testes
        jdbc.update("update user_settings set digest_time = null");
        when(email.type()).thenReturn(ChannelType.EMAIL);
        when(push.type()).thenReturn(ChannelType.PUSH);
    }

    protected String compromisso(String json) throws Exception {
        String body = mockMvc.perform(post("/api/commitments").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    protected Reminder lembrete(String commitmentId, int minutos, ChannelType... canais) {
        return reminders.save(new Reminder(userId, UUID.fromString(commitmentId), minutos, EnumSet.copyOf(List.of(canais))));
    }

    protected void cron() throws Exception {
        mockMvc.perform(post("/internal/reminders/dispatch").header(InternalCronController.SECRET_HEADER, CRON_SECRET))
                .andExpect(status().isOk());
    }
}

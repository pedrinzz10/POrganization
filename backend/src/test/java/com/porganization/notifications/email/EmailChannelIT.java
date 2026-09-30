package com.porganization.notifications.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.porganization.notifications.Notification;
import com.porganization.support.IntegrationTest;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class EmailChannelIT extends IntegrationTest {

    // SMTP de mentira na porta 3025, sem autenticação nem TLS
    @RegisterExtension
    static final GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void smtpDeTeste(DynamicPropertyRegistry registry) {
        registry.add("MAIL_HOST", () -> "localhost");
        registry.add("MAIL_PORT", () -> ServerSetupTest.SMTP.getPort());
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("MAIL_FROM", () -> "POrganization <avisos@porganization.test>");
        registry.add("APP_URL", () -> "https://porganization.test");
    }

    @Autowired
    private EmailChannel email;

    private final UUID userId = UUID.randomUUID();

    // I02 T1 (CA1)
    @Test
    void lembreteDoDentistaChegaNoEmailDoUsuarioComOAssuntoEsperado() throws Exception {
        jdbc.update("insert into user_settings (user_id, email) values (?, 'pedro@teste.com')", userId);

        email.send(new Notification(userId, Notification.Kind.REMINDER, "Lembrete: Dentista às 15:00",
                List.of("quinta-feira, 1 de outubro às 15:00", "Rua das Flores, 10"), "/hoje"));

        MimeMessage[] recebidas = smtp.getReceivedMessages();
        assertThat(recebidas).hasSize(1);
        MimeMessage mensagem = recebidas[0];
        assertThat(mensagem.getSubject()).isEqualTo("Lembrete: Dentista às 15:00");
        assertThat(mensagem.getAllRecipients()[0].toString()).isEqualTo("pedro@teste.com");
        assertThat(mensagem.getFrom()[0].toString()).contains("avisos@porganization.test");
        String corpo = GreenMailUtil.getBody(mensagem);
        assertThat(corpo).contains("Rua das Flores, 10").contains("https://porganization.test/hoje");
    }

    @Test
    void usuarioSemEmailFalhaSemEnviar() {
        assertThatThrownBy(() -> email.send(new Notification(UUID.randomUUID(), Notification.Kind.REMINDER, "Lembrete: X às 10:00",
                List.of("hoje"), "/hoje")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem e-mail");
        assertThat(smtp.getReceivedMessages()).isEmpty();
    }
}

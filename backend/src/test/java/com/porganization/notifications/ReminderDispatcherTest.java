package com.porganization.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.porganization.commitments.CommitmentService;
import com.porganization.commitments.dto.OccurrenceResponse;
import com.porganization.notifications.email.EmailChannel;
import com.porganization.settings.UserSettingsService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** O dispatcher com o EmailChannel de verdade e um JavaMailSender de mentira. */
class ReminderDispatcherTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.parse("2026-10-01");

    private final UUID userId = UUID.randomUUID();
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger dispatcherLog = (Logger) LoggerFactory.getLogger(ReminderDispatcher.class);

    @BeforeEach
    void capturarLog() {
        logs.start();
        dispatcherLog.addAppender(logs);
    }

    @AfterEach
    void soltarLog() {
        dispatcherLog.detachAppender(logs);
    }

    // I02 T2 (CA2)
    @Test
    void falhaNoSmtpDoPrimeiroNaoImpedeOSegundoEFicaNoLog() throws Exception {
        Reminder dentista = lembrete(UUID.randomUUID());
        Reminder reuniao = lembrete(UUID.randomUUID());
        ReminderRepository reminders = mock(ReminderRepository.class);
        when(reminders.findAll()).thenReturn(List.of(dentista, reuniao));

        CommitmentService commitments = mock(CommitmentService.class);
        when(commitments.findInRange(eq(userId), any(), any())).thenReturn(List.of(
                ocorrencia(dentista.getCommitmentId(), "Dentista"), ocorrencia(reuniao.getCommitmentId(), "Reunião")));

        UserSettingsService settings = mock(UserSettingsService.class);
        when(settings.zoneOf(userId)).thenReturn(SAO_PAULO);
        when(settings.emailOf(userId)).thenReturn(Optional.of("pedro@teste.com"));

        NotificationLog notificationLog = mock(NotificationLog.class);
        when(notificationLog.claim(any(), any(), any())).thenReturn(true);

        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("SMTP recusou")).doNothing().when(mailSender).send(any(MimeMessage.class));

        EmailChannel email = new EmailChannel(mailSender, templateEngine(), settings, "avisos@porganization.test", "https://app.test");
        // 14:31 em São Paulo; os dois compromissos são às 15:00 com lembrete de 30 min
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T17:31:00Z"), SAO_PAULO);
        ReminderDispatcher dispatcher = new ReminderDispatcher(reminders, commitments, settings, notificationLog, List.of(email), clock);

        ReminderDispatcher.Result result = dispatcher.dispatch();

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(mailSender, times(2)).send(any(MimeMessage.class));
        // A reserva do que falhou é liberada para a próxima chamada tentar de novo
        verify(notificationLog).release(eq(dentista.getId()), eq(HOJE), eq(ChannelType.EMAIL));
        assertThat(logs.list).anySatisfy(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.ERROR);
            assertThat(evento.getFormattedMessage()).contains("Falha ao enviar lembrete por EMAIL").contains("SMTP recusou");
        });
    }

    private Reminder lembrete(UUID commitmentId) {
        Reminder reminder = new Reminder(userId, commitmentId, 30, Set.of(ChannelType.EMAIL));
        ReflectionTestUtils.setField(reminder, "id", UUID.randomUUID());
        return reminder;
    }

    private static OccurrenceResponse ocorrencia(UUID commitmentId, String titulo) {
        return new OccurrenceResponse(commitmentId, HOJE, titulo, LocalTime.of(15, 0), null, false, false, false, null, null);
    }

    private static TemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        // Como no app: expressões em SpEL (o TemplateEngine puro pediria OGNL)
        TemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}

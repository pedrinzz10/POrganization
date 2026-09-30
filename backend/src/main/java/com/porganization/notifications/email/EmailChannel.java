package com.porganization.notifications.email;

import com.porganization.notifications.ChannelType;
import com.porganization.notifications.Notification;
import com.porganization.notifications.NotificationChannel;
import com.porganization.settings.UserSettingsService;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Envia avisos por e-mail (SMTP do Spring Mail, configurado por MAIL_HOST, MAIL_USERNAME,
 * MAIL_PASSWORD e MAIL_FROM). O corpo é um template Thymeleaf simples; o destinatário é o
 * e-mail do usuário em user_settings, preenchido pelo GET /api/me a partir do token.
 */
@Component("emailChannel")
public class EmailChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final TemplateEngine templates;
    private final UserSettingsService userSettings;
    private final String from;
    private final String appUrl;

    public EmailChannel(JavaMailSender mailSender, TemplateEngine templates, UserSettingsService userSettings,
            @Value("${porganization.mail.from:}") String from, @Value("${porganization.app-url}") String appUrl) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.userSettings = userSettings;
        this.from = from;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(Notification notification) throws Exception {
        if (from.isBlank()) {
            throw new IllegalStateException("MAIL_FROM não configurado");
        }
        String to = userSettings.emailOf(notification.userId())
                .orElseThrow(() -> new IllegalStateException("usuário sem e-mail em user_settings"));

        Context context = new Context();
        context.setVariable("subject", notification.subject());
        context.setVariable("lines", notification.lines());
        context.setVariable("link", appUrl + notification.url());
        String html = templates.process(template(notification.kind()), context);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(notification.subject());
        helper.setText(html, true);
        mailSender.send(message);
    }

    private static String template(Notification.Kind kind) {
        return switch (kind) {
            case REMINDER, SCHEDULED_NOTICE, TASK_REMINDER -> "reminder-email";
            case DAILY_DIGEST -> "daily-digest-email";
        };
    }
}

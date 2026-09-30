package com.porganization.notifications;

/**
 * Um jeito de entregar avisos (e-mail, push). O ReminderDispatcher escolhe o canal pelo type()
 * de cada lembrete; uma exceção em send() conta como falha só daquele envio.
 */
public interface NotificationChannel {

    ChannelType type();

    void send(Notification notification) throws Exception;
}

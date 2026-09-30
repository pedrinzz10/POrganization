package com.porganization.notifications;

import com.porganization.settings.UserSettings;
import com.porganization.settings.UserSettingsService;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Os lembretes de cada compromisso: o padrão das configurações na criação, ou os que vierem no corpo. */
@Service
public class ReminderService {

    private final ReminderRepository reminders;
    private final UserSettingsService userSettings;

    public ReminderService(ReminderRepository reminders, UserSettingsService userSettings) {
        this.reminders = reminders;
        this.userSettings = userSettings;
    }

    @Transactional(readOnly = true)
    public List<ReminderSpec> of(UUID userId, UUID commitmentId) {
        return reminders.findByUserIdAndCommitmentIdOrderByMinutesBeforeAsc(userId, commitmentId).stream()
                .map(r -> new ReminderSpec(r.getMinutesBefore(), EnumSet.copyOf(r.getChannels())))
                .toList();
    }

    /** Compromisso novo: sem "reminders" no corpo (criação rápida), ganha o lembrete padrão, se houver. */
    @Transactional
    public void onCreate(UUID userId, UUID commitmentId, List<ReminderSpec> requested) {
        replace(userId, commitmentId, requested != null ? requested : defaults(userId));
    }

    /** Edição: sem "reminders" no corpo, mantém os atuais; com uma lista (mesmo vazia), troca por ela. */
    @Transactional
    public void onUpdate(UUID userId, UUID commitmentId, List<ReminderSpec> requested) {
        if (requested != null) {
            replace(userId, commitmentId, requested);
        }
    }

    private void replace(UUID userId, UUID commitmentId, List<ReminderSpec> wanted) {
        reminders.findByUserIdAndCommitmentIdOrderByMinutesBeforeAsc(userId, commitmentId).forEach(reminders::delete);
        // Dois lembretes com a mesma antecedência viram um só, com a união dos canais
        wanted.stream()
                .collect(Collectors.groupingBy(ReminderSpec::minutesBefore,
                        Collectors.flatMapping(r -> r.channels().stream(), Collectors.toCollection(() -> EnumSet.noneOf(ChannelType.class)))))
                .entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey()))
                .forEach(e -> reminders.save(new Reminder(userId, commitmentId, e.getKey(), e.getValue())));
    }

    private List<ReminderSpec> defaults(UUID userId) {
        UserSettings settings = userSettings.settingsOf(userId);
        if (settings.getDefaultReminderMinutes() == null) {
            return List.of();
        }
        Set<ChannelType> channels = settings.getNotifyChannels().stream().map(ChannelType::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ChannelType.class)));
        return List.of(new ReminderSpec(settings.getDefaultReminderMinutes(), channels));
    }
}

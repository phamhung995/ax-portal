package com.furelise.skillmanagement.notification.service.impl;

import com.furelise.skillmanagement.notification.model.Notification;
import com.furelise.skillmanagement.notification.repository.NotificationRepository;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.notification.service.NotificationEmailSender;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Default {@link EventNotificationService} (see the interface javadoc for
 * the guarantees).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventNotificationServiceImpl implements EventNotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationEmailSender notificationEmailSender;
    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Override
    public boolean notify(User actor, User recipient, UserSettings settings, EventDetails details) {
        if (actor == null || recipient == null || details == null) {
            return false;
        }
        if (recipient.getId().equals(actor.getId())) {
            return false; // never notify the actor about their own event
        }
        if (!recipient.isEnabled()) {
            return false; // disabled users receive no notifications
        }
        UserSettings effective = settings != null ? settings
                : userSettingsRepository.findById(recipient.getId()).orElse(null);
        return send(recipient, effective, details);
    }

    @Override
    public int notifyBatch(User actor, Map<Long, User> recipients,
                           Map<Long, UserSettings> settings,
                           java.util.function.Function<Long, EventDetails> detailsFor) {
        int sent = 0;
        for (Map.Entry<Long, User> entry : recipients.entrySet()) {
            User recipient = entry.getValue();
            if (recipient == null) {
                continue;
            }
            if (notify(actor, recipient,
                    settings != null ? settings.get(entry.getKey()) : null,
                    detailsFor.apply(entry.getKey()))) {
                sent++;
            }
        }
        return sent;
    }

    private boolean send(User recipient, UserSettings settings, EventDetails details) {
        boolean inapp = settings == null || settings.isInappNotify();
        boolean email = settings == null || settings.isEmailNotify();
        boolean sent = false;
        if (inapp) {
            EventContext ctx = details.context() != null ? details.context() : EventContext.none();
            notificationRepository.save(Notification.builder()
                    .userId(recipient.getId())
                    .title(details.title())
                    .skillId(ctx.skillId())
                    .feedbackId(ctx.feedbackId())
                    .requestId(ctx.requestId())
                    .newsId(ctx.newsId())
                    .commentId(ctx.commentId())
                    .read(false)
                    .build());
            sent = true;
        }
        if (email && details.emailSubject() != null) {
            // Async: the Knox Mail API round-trip must not hold the caller's
            // transaction open (see NotificationEmailSender). The failure is
            // handled (logged) inside the sender.
            notificationEmailSender.sendAsync(recipient.getId(),
                    recipient.getEmail(), details.emailSubject(), details.emailBody());
            sent = true;
        }
        return sent;
    }
}

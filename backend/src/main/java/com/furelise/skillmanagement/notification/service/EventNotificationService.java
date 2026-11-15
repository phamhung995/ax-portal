package com.furelise.skillmanagement.notification.service;

import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;

import java.util.Map;

/**
 * Event notifications (in-app bell row and/or email) for domain events —
 * @mentions, forum replies, ratings, workflow transitions, ...
 *
 * <p>Centralizes the "honor the recipient's notification settings" logic
 * that was copy-pasted across services. Guarantees:</p>
 * <ul>
 *   <li>the actor is never notified about their own event</li>
 *   <li>missing or <b>disabled</b> recipients are skipped (no in-app row,
 *       no email)</li>
 *   <li>in-app and email are sent independently per the recipient's
 *       {@link UserSettings} (default: both on when no settings row)</li>
 *   <li>email failures are best-effort (logged, never thrown)</li>
 * </ul>
 *
 * <p>Participates in the caller's transaction (no annotation of its own) —
 * the in-app row is written atomically with the caller's event transaction
 * or the caller's explicit notification transaction.</p>
 */
public interface EventNotificationService {

    /**
     * Notification link targets (all nullable — the bell deep-links on
     * whichever is set).
     */
    record EventContext(String skillId, Long feedbackId, Long requestId,
                        Long newsId, Long commentId) {
        public static EventContext none() {
            return new EventContext(null, null, null, null, null);
        }
    }

    /**
     * Notification payload for one recipient (title + link targets +
     * optional email).
     */
    record EventDetails(String title, EventContext context,
                        String emailSubject, String emailBody) {
        /** In-app-only event (no email). */
        EventDetails(String title, EventContext context) {
            this(title, context, null, null);
        }
    }

    /**
     * Notifies one recipient.
     *
     * @param settings  the recipient's settings (nullable — null = both
     *                  channels on, the default when no settings row)
     * @return true when at least one notification (in-app and/or email)
     *         was actually sent; false when skipped (actor, missing or
     *         disabled recipient) or the recipient disabled both channels
     */
    boolean notify(User actor, User recipient, UserSettings settings, EventDetails details);

    /**
     * Batch variant for events with many recipients (e.g. @mentions):
     * iterates the already-fetched recipients and notifies each.
     *
     * @param recipients  recipientId → user (pre-fetched by the caller)
     * @param settings    recipientId → settings (pre-fetched; missing =
     *                    both channels on)
     * @param detailsFor  supplier of the per-recipient payload (called
     *                    only for recipients that pass the skip checks)
     * @return the number of recipients that received at least one
     *         notification (skipped recipients do not count)
     */
    int notifyBatch(User actor, Map<Long, User> recipients,
                    Map<Long, UserSettings> settings,
                    java.util.function.Function<Long, EventDetails> detailsFor);
}

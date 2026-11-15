package com.furelise.skillmanagement.ainews.service.impl;

import com.furelise.skillmanagement.ainews.dto.AiNewsCommentItem;
import com.furelise.skillmanagement.ainews.dto.AiNewsCommentRequest;
import com.furelise.skillmanagement.ainews.email.CommentHtmlSanitizer;
import com.furelise.skillmanagement.ainews.model.AiNews;
import com.furelise.skillmanagement.ainews.model.AiNewsComment;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.ainews.repository.AiNewsCommentRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsCommentVoteRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.mail.EmailService;
import com.furelise.skillmanagement.notification.model.Notification;
import com.furelise.skillmanagement.notification.repository.NotificationRepository;
import com.furelise.skillmanagement.notification.service.impl.EventNotificationServiceImpl;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiNewsCommentServiceImplTest {

    private static final Long ACTOR_ID = 1L;
    private static final Long MENTIONED_ID = 2L;
    private static final Long NEWS_ID = 45L;

    @Mock private AiNewsRepository aiNewsRepository;
    @Mock private AiNewsCommentRepository commentRepository;
    @Mock private AiNewsCommentVoteRepository voteRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private EmailService emailService;
    @Mock private com.furelise.skillmanagement.common.RateLimiter rateLimiter;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private AiNewsCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        // The mention path wraps its writes in a TransactionTemplate (the
        // afterCommit callback runs with the comment transaction already
        // closed). Stub the mocked transaction manager so the template
        // behaves like a real no-rollback commit.
        org.springframework.transaction.TransactionStatus status =
                org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class);
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(
                        org.springframework.transaction.TransactionDefinition.class)))
                .thenReturn(status);
        // Default: the anti-spam throttle allows the comment (the dedicated
        // rate-limit test stubs it to deny).
        org.mockito.Mockito.lenient().when(rateLimiter.allow(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(true);
        service = new AiNewsCommentServiceImpl(
                aiNewsRepository, commentRepository, voteRepository,
                userRepository, userSettingsRepository,
                new EventNotificationServiceImpl(notificationRepository,
                        new com.furelise.skillmanagement.notification.service.NotificationEmailSender(
                                emailService),
                        userRepository, userSettingsRepository),
                new CommentHtmlSanitizer(), rateLimiter, transactionManager);
    }

    private User user(Long id, String name) {
        return User.builder().id(id).name(name).email(name.toLowerCase().replace(" ", ".") + "@test.com")
                .enabled(true).build();
    }

    private AiNews activeNews() {
        return AiNews.builder().newsId(NEWS_ID).title("Test News").archiveYn("N").build();
    }

    /** commentRepository.save assigns an id (mimics JPA identity insert). */
    private void stubCommentSave() {
        when(commentRepository.save(any(AiNewsComment.class))).thenAnswer(inv -> {
            AiNewsComment c = inv.getArgument(0);
            c.setCommentId(100L);
            return c;
        });
    }

    @Test
    @DisplayName("addComment: mention of another user → in-app + email notification")
    void mentionNotifiesOtherUser() {
        User actor = user(ACTOR_ID, "Alice");
        User mentioned = user(MENTIONED_ID, "Bob");
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        stubCommentSave();
        when(userRepository.findByIdIn(anyCollection())).thenReturn(List.of(mentioned));
        when(userSettingsRepository.findAllById(anyCollection()))
                .thenReturn(List.of(UserSettings.builder().userId(MENTIONED_ID)
                        .inappNotify(true).emailNotify(true).build()));

        String content = "<p>Hi <span data-type=\"mention\" data-user-id=\"2\" "
                + "data-user-name=\"Bob\">@Bob</span> please check</p>";
        AiNewsCommentItem item = service.addComment(NEWS_ID, actor,
                new AiNewsCommentRequest(content, null));

        assertThat(item.commentId()).isEqualTo(100L);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        Notification n = cap.getValue();
        assertThat(n.getUserId()).isEqualTo(MENTIONED_ID);
        assertThat(n.getNewsId()).isEqualTo(NEWS_ID);
        assertThat(n.getCommentId()).isEqualTo(100L);
        assertThat(n.isRead()).isFalse();
        assertThat(n.getTitle()).contains("Alice").contains("Test News");

        verify(emailService).sendNotificationEmail(eq("bob@test.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("addComment: self-mention → no notification")
    void selfMentionNotNotified() {
        User actor = user(ACTOR_ID, "Alice");
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        stubCommentSave();
        when(userRepository.findByIdIn(anyCollection())).thenReturn(List.of(actor));

        String content = "<p>note to <span data-type=\"mention\" data-user-id=\"1\" "
                + "data-user-name=\"Alice\">@Alice</span></p>";
        service.addComment(NEWS_ID, actor, new AiNewsCommentRequest(content, null));

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(emailService);
        // userSettingsRepository IS queried (batch fetch happens before the
        // self-mention skip) — but no notifications are sent.
    }

    @Test
    @DisplayName("addComment: disabled mentioned user → no notification")
    void disabledUserNotNotified() {
        User actor = user(ACTOR_ID, "Alice");
        User disabled = user(MENTIONED_ID, "Bob");
        disabled.setEnabled(false);
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        stubCommentSave();
        when(userRepository.findByIdIn(anyCollection())).thenReturn(List.of(disabled));

        String content = "<p><span data-type=\"mention\" data-user-id=\"2\" "
                + "data-user-name=\"Bob\">@Bob</span></p>";
        service.addComment(NEWS_ID, actor, new AiNewsCommentRequest(content, null));

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("addComment: email failure does not break comment creation")
    void emailFailureDoesNotBreakComment() {
        User actor = user(ACTOR_ID, "Alice");
        User mentioned = user(MENTIONED_ID, "Bob");
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        stubCommentSave();
        when(userRepository.findByIdIn(anyCollection())).thenReturn(List.of(mentioned));
        when(userSettingsRepository.findAllById(anyCollection()))
                .thenReturn(List.of(UserSettings.builder().userId(MENTIONED_ID)
                        .inappNotify(true).emailNotify(true).build()));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down"))
                .when(emailService).sendNotificationEmail(anyString(), anyString(), anyString());

        String content = "<p><span data-type=\"mention\" data-user-id=\"2\" "
                + "data-user-name=\"Bob\">@Bob</span></p>";
        AiNewsCommentItem item = service.addComment(NEWS_ID, actor,
                new AiNewsCommentRequest(content, null));

        // comment still created; in-app notification still saved
        assertThat(item.commentId()).isEqualTo(100L);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("addComment: more than 10 distinct mentions → notifications capped at 10")
    void mentionCapEnforced() {
        User actor = user(ACTOR_ID, "Alice");
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        stubCommentSave();
        // 12 distinct mentioned users (ids 2..13) — only the first 10 are notified.
        StringBuilder sb = new StringBuilder("<p>");
        for (long id = 2; id <= 13; id++) {
            sb.append("<span data-type=\"mention\" data-user-id=\"").append(id)
                    .append("\" data-user-name=\"U").append(id).append("\">@U").append(id).append("</span>");
        }
        sb.append("</p>");
        // Batch fetch returns users 2..11 (the first 10 mentioned).
        List<User> users = new java.util.ArrayList<>();
        List<UserSettings> settings = new java.util.ArrayList<>();
        for (long id = 2; id <= 11; id++) {
            users.add(user(id, "U" + id));
            settings.add(UserSettings.builder().userId(id)
                    .inappNotify(true).emailNotify(true).build());
        }
        when(userRepository.findByIdIn(anyCollection())).thenReturn(users);
        when(userSettingsRepository.findAllById(anyCollection())).thenReturn(settings);

        service.addComment(NEWS_ID, actor, new AiNewsCommentRequest(sb.toString(), null));

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(10)).save(cap.capture());
        cap.getAllValues().forEach(n -> {
            assertThat(n.getUserId()).isBetween(2L, 11L);
        });
        verify(emailService, org.mockito.Mockito.times(10))
                .sendNotificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("addComment: rate limit exceeded → COMMENT_RATE_LIMITED, nothing saved")
    void commentRateLimited() {
        User actor = user(ACTOR_ID, "Alice");
        when(rateLimiter.allow("ai-news-comment:" + ACTOR_ID, 10, 60)).thenReturn(false);

        assertThatThrownBy(() -> service.addComment(NEWS_ID, actor,
                new AiNewsCommentRequest("<p>spam</p>", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.COMMENT_RATE_LIMITED);

        verifyNoInteractions(commentRepository);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("addComment: comment without mentions → no notification")
    void plainCommentNotNotified() {
        User actor = user(ACTOR_ID, "Alice");
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        stubCommentSave();

        service.addComment(NEWS_ID, actor, new AiNewsCommentRequest("<p>plain text</p>", null));

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(emailService);
        verify(commentRepository, never()).incrementReplyCount(anyLong());
    }

    // ── Threading (FR-17 v1.18: nested depth 3, no flattening) ───────────────

    private AiNewsComment comment(Long id, Long parentId, Long deletedAt) {
        AiNewsComment c = AiNewsComment.builder()
                .newsId(NEWS_ID).userId(ACTOR_ID).parentId(parentId).content("<p>x</p>")
                .build();
        c.setCommentId(id);
        if (deletedAt != null) {
            c.setDeletedAt(java.time.Instant.ofEpochMilli(deletedAt));
        }
        return c;
    }

    @Test
    @DisplayName("addComment: reply to a depth-2 comment → parentId kept (no flatten)")
    void replyToDepth2KeepsParent() {
        User actor = user(ACTOR_ID, "Alice");
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        stubCommentSave();
        AiNewsComment root = comment(1L, null, null);
        AiNewsComment depth2 = comment(2L, 1L, null);
        when(commentRepository.findById(2L)).thenReturn(java.util.Optional.of(depth2));
        when(commentRepository.findById(1L)).thenReturn(java.util.Optional.of(root));

        AiNewsCommentItem item = service.addComment(NEWS_ID, actor,
                new AiNewsCommentRequest("<p>reply</p>", 2L));

        assertThat(item.parentId()).isEqualTo(2L);
        verify(commentRepository).incrementReplyCount(2L);
        verify(commentRepository, never()).incrementReplyCount(1L);
    }

    @Test
    @DisplayName("addComment: reply to a depth-3 comment → COMMENT_DEPTH_EXCEEDED, nothing saved")
    void replyToDepth3Blocked() {
        User actor = user(ACTOR_ID, "Alice");
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        AiNewsComment root = comment(1L, null, null);
        AiNewsComment depth2 = comment(2L, 1L, null);
        AiNewsComment depth3 = comment(3L, 2L, null);
        when(commentRepository.findById(3L)).thenReturn(java.util.Optional.of(depth3));
        when(commentRepository.findById(2L)).thenReturn(java.util.Optional.of(depth2));
        when(commentRepository.findById(1L)).thenReturn(java.util.Optional.of(root));

        assertThatThrownBy(() -> service.addComment(NEWS_ID, actor,
                new AiNewsCommentRequest("<p>too deep</p>", 3L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.COMMENT_DEPTH_EXCEEDED);

        verify(commentRepository, never()).save(any(AiNewsComment.class));
    }

    @Test
    @DisplayName("addComment: reply to a deleted depth-2 parent → COMMENT_PARENT_DELETED")
    void replyToDeletedDepth2Blocked() {
        User actor = user(ACTOR_ID, "Alice");
        when(aiNewsRepository.findByNewsIdAndArchiveYn(NEWS_ID, "N")).thenReturn(activeNews());
        AiNewsComment depth2 = comment(2L, 1L, 1000L);
        when(commentRepository.findById(2L)).thenReturn(java.util.Optional.of(depth2));

        assertThatThrownBy(() -> service.addComment(NEWS_ID, actor,
                new AiNewsCommentRequest("<p>reply</p>", 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.COMMENT_PARENT_DELETED);

        verify(commentRepository, never()).save(any(AiNewsComment.class));
    }

    @Test
    @DisplayName("comments: depth-3 reply under a deleted depth-2 parent is hidden (FR-19)")
    void depth3UnderDeletedParentHidden() {
        User viewer = user(ACTOR_ID, "Alice");
        when(aiNewsRepository.countActiveById(NEWS_ID)).thenReturn(1L);
        AiNewsComment root = comment(1L, null, null);
        AiNewsComment depth2 = comment(2L, 1L, 1000L); // deleted
        AiNewsComment depth3 = comment(3L, 2L, null);   // live, but parent deleted
        when(commentRepository.findBoundedByNewsId(NEWS_ID, 100, 50))
                .thenReturn(List.of(root, depth2, depth3));
        when(userRepository.findByIdIn(anyCollection())).thenReturn(List.of(viewer));
        when(voteRepository.findByUserIdAndCommentIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());

        List<AiNewsCommentItem> items = service.comments(NEWS_ID, viewer);

        // root stays; the deleted depth-2 reply is hidden (tombstone only
        // applies to top-level, FR-19); the live depth-3 under the deleted
        // parent is hidden too (its slot is gone — FR-17 v1.21).
        assertThat(items).extracting(AiNewsCommentItem::commentId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("comments: deleted top-level with a live reply → tombstone + reply kept (FR-19)")
    void deletedTopLevelTombstoneKept() {
        User viewer = user(ACTOR_ID, "Alice");
        when(aiNewsRepository.countActiveById(NEWS_ID)).thenReturn(1L);
        AiNewsComment root = comment(1L, null, 1000L); // deleted top-level
        AiNewsComment depth2 = comment(2L, 1L, null);  // live reply
        when(commentRepository.findBoundedByNewsId(NEWS_ID, 100, 50))
                .thenReturn(List.of(root, depth2));
        when(userRepository.findByIdIn(anyCollection())).thenReturn(List.of(viewer));
        when(voteRepository.findByUserIdAndCommentIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());

        List<AiNewsCommentItem> items = service.comments(NEWS_ID, viewer);

        // Both rows are returned: the tombstoned root (content null, deleted
        // true) and its live reply — the reply context must survive.
        assertThat(items).extracting(AiNewsCommentItem::commentId)
                .containsExactlyInAnyOrder(1L, 2L);
        AiNewsCommentItem tombstone = items.stream()
                .filter(i -> i.commentId() == 1L).findFirst().orElseThrow();
        assertThat(tombstone.deleted()).isTrue();
        assertThat(tombstone.content()).isNull();
    }
}

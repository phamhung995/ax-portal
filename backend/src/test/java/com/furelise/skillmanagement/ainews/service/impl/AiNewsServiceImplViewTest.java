package com.furelise.skillmanagement.ainews.service.impl;

import com.furelise.skillmanagement.ainews.repository.AiNewsLikeRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsViewLogRepository;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the view-tracking path (POST /{newsId}/view):
 * upsert + recompute ordering, idempotency contract (no exists pre-check),
 * and the archived-news 404 guard.
 */
@ExtendWith(MockitoExtension.class)
class AiNewsServiceImplViewTest {

    private static final Long NEWS_ID = 45L;
    private static final Long USER_ID = 7L;

    @Mock private AiNewsRepository aiNewsRepository;
    @Mock private AiNewsLikeRepository likeRepository;
    @Mock private AiNewsViewLogRepository viewLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private AiNewsServiceImpl service;

    @BeforeEach
    void setUp() {
        // recordView wraps the upsert in a REQUIRES_NEW TransactionTemplate
        // (viewTransaction). Stub the mocked transaction manager so the
        // template behaves like a real no-rollback commit/rollback — without
        // this, getTransaction() returns null and the template NPEs.
        org.springframework.transaction.TransactionStatus status =
                org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class);
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(
                        org.springframework.transaction.TransactionDefinition.class)))
                .thenReturn(status);
        service = new AiNewsServiceImpl(
                aiNewsRepository, likeRepository, viewLogRepository,
                userRepository, transactionManager);
    }

    private User user() {
        return User.builder().id(USER_ID).name("Alice").email("alice@test.com")
                .enabled(true).build();
    }

    @Test
    @DisplayName("recordView: first-time view → upsert then recompute, returns distinct count")
    void recordViewUpsertsAndRecomputes() {
        when(aiNewsRepository.countActiveById(NEWS_ID)).thenReturn(1L);
        when(viewLogRepository.upsertView(NEWS_ID, USER_ID)).thenReturn(1);
        when(viewLogRepository.countByNewsId(NEWS_ID)).thenReturn(3L);

        int count = service.recordView(NEWS_ID, user());

        assertThat(count).isEqualTo(3);
        // Order matters: the row must exist before the counter is
        // recomputed from it (recompute reads the log).
        var order = inOrder(viewLogRepository);
        order.verify(viewLogRepository).upsertView(NEWS_ID, USER_ID);
        order.verify(viewLogRepository).recomputeViewCount(NEWS_ID);
        order.verify(viewLogRepository).countByNewsId(NEWS_ID);
    }

    @Test
    @DisplayName("recordView: repeat view (upsert no-op) → no recompute, reads the denormalized counter")
    void recordViewRepeatSkipsRecompute() {
        when(aiNewsRepository.countActiveById(NEWS_ID)).thenReturn(1L);
        when(viewLogRepository.upsertView(NEWS_ID, USER_ID)).thenReturn(0);
        when(aiNewsRepository.findActiveViewCountById(NEWS_ID)).thenReturn(7);

        int count = service.recordView(NEWS_ID, user());

        assertThat(count).isEqualTo(7);
        // The counter is already current — no row-locking UPDATE + COUNT(*).
        verify(viewLogRepository, org.mockito.Mockito.never()).recomputeViewCount(NEWS_ID);
        verify(viewLogRepository, org.mockito.Mockito.never()).countByNewsId(NEWS_ID);
    }

    @Test
    @DisplayName("recordView: no exists pre-check — the guarded upsert is the idempotency guard")
    void recordViewSkipsExistsPreCheck() {
        when(aiNewsRepository.countActiveById(NEWS_ID)).thenReturn(1L);
        when(viewLogRepository.upsertView(NEWS_ID, USER_ID)).thenReturn(1);
        when(viewLogRepository.countByNewsId(NEWS_ID)).thenReturn(1L);

        service.recordView(NEWS_ID, user());

        // A pre-check (existsByNewsIdAndUserId) would be TOCTOU — two
        // concurrent first-time views would both pass it and both
        // increment. The guarded upsert alone must be the guard.
        verify(viewLogRepository).upsertView(NEWS_ID, USER_ID);
        verify(viewLogRepository).recomputeViewCount(NEWS_ID);
    }

    @Test
    @DisplayName("recordView: concurrent duplicate insert (unique constraint) → no-op, count still correct")
    void recordViewRaceLoserIsNoOp() {
        when(aiNewsRepository.countActiveById(NEWS_ID)).thenReturn(1L);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint uq_ai_news_view_log"))
                .when(viewLogRepository).upsertView(NEWS_ID, USER_ID);
        when(aiNewsRepository.findActiveViewCountById(NEWS_ID)).thenReturn(1);

        // The losing race must not propagate. The loser's recompute is
        // skipped (its transaction is aborted on PostgreSQL — every further
        // statement would fail), but the count is still correct: the
        // WINNER's transaction already ran the idempotent recompute, so the
        // denormalized counter read below is current.
        int count = service.recordView(NEWS_ID, user());

        assertThat(count).isEqualTo(1);
        org.mockito.Mockito.verify(viewLogRepository, org.mockito.Mockito.never())
                .recomputeViewCount(NEWS_ID);
    }

    @Test
    @DisplayName("recordView: archived/missing news → NEWS_NOT_FOUND, no view log written")
    void recordViewArchivedNewsThrows() {
        when(aiNewsRepository.countActiveById(NEWS_ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.recordView(NEWS_ID, user()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(BusinessErrorCode.NEWS_NOT_FOUND);

        verifyNoInteractions(viewLogRepository);
    }
}

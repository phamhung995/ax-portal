package com.furelise.skillmanagement.reputation;

import com.furelise.skillmanagement.ainews.repository.AiNewsCommentVoteRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsLikeRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsViewLogRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolDownloadLogRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolRepository;
import com.furelise.skillmanagement.mcp.repository.McpDownloadLogRepository;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpRatingRepository;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.repository.ContributorReputationRepository;
import com.furelise.skillmanagement.reputation.repository.ReputationScoreRepository;
import com.furelise.skillmanagement.reputation.service.impl.ReputationServiceImpl;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillDownloadLogRepository;
import com.furelise.skillmanagement.skill.repository.SkillRatingRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skillrequest.model.SkillRequest;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestVoteRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the GĐ5 request score (BA §9.1, Q6/Q8 chốt 18/11):
 * score = min(verifiedVotes, 50)/50 * 100 * 0.5^(ageDays/30), +15 when
 * FULFILLED (capped at 100). NEW below 5 verified votes (score NULL).
 */
@ExtendWith(MockitoExtension.class)
class ReputationSkillRequestTest {

    @Mock private ReputationScoreRepository scoreRepository;
    @Mock private ContributorReputationRepository contributorRepository;
    @Mock private SkillRatingRepository ratingRepository;
    @Mock private SkillDownloadLogRepository downloadLogRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private McpRatingRepository mcpRatingRepository;
    @Mock private McpDownloadLogRepository mcpDownloadLogRepository;
    @Mock private McpMasterRepository mcpMasterRepository;
    @Mock private AiToolRepository aiToolRepository;
    @Mock private AiToolDownloadLogRepository aiToolDownloadLogRepository;
    @Mock private AiNewsRepository aiNewsRepository;
    @Mock private AiNewsLikeRepository aiNewsLikeRepository;
    @Mock private AiNewsViewLogRepository aiNewsViewLogRepository;
    @Mock private AiNewsCommentVoteRepository aiNewsCommentVoteRepository;
    @Mock private SkillRequestRepository skillRequestRepository;
    @Mock private SkillRequestVoteRepository skillRequestVoteRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ReputationServiceImpl service;

    private SkillRequest request(long verifiedVotes, long totalVotes, int ageDays, String status) {
        return SkillRequest.builder()
                .id(1L)
                .title("Need a skill")
                .voteCount((int) totalVotes)
                .createdBy(2L)
                .createdAt(Instant.now().minusSeconds(ageDays * 86400L))
                .fulfillmentStatus(status)
                .build();
    }

    private void stubRequest(SkillRequest req, long verifiedVotes) {
        when(skillRequestRepository.findById(1L)).thenReturn(Optional.of(req));
        when(skillRequestVoteRepository.countVerifiedByRequestId(1L)).thenReturn(verifiedVotes);
        when(scoreRepository.findById(any())).thenReturn(Optional.empty());
        when(scoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void belowMinimumSample_isNewWithNullScore() {
        stubRequest(request(4, 4, 0, SkillRequest.FULFILLMENT_OPEN), 4);
        ReputationScore row = service.refreshSkillRequest(1L);
        assertNull(row.getScore());
        assertEquals("NEW", row.getTier());
        assertEquals(4, row.getRatingCount());
    }

    @Test
    void fiveVotes_scoresTen_new() {
        stubRequest(request(5, 5, 0, SkillRequest.FULFILLMENT_OPEN), 5);
        ReputationScore row = service.refreshSkillRequest(1L);
        assertEquals(new BigDecimal("10.00"), row.getScore());
        assertEquals("NEW", row.getTier());
    }

    @Test
    void fortyVotes_scoresEighty_trusted() {
        stubRequest(request(40, 40, 0, SkillRequest.FULFILLMENT_OPEN), 40);
        ReputationScore row = service.refreshSkillRequest(1L);
        assertEquals(new BigDecimal("80.00"), row.getScore());
        assertEquals("TRUSTED", row.getTier());
    }

    @Test
    void fortyVotesFulfilled_scoresNinetyFive_popular() {
        SkillRequest req = request(40, 40, 0, SkillRequest.FULFILLMENT_FULFILLED);
        req.setFulfilledSkillId(10L);
        stubRequest(req, 40);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(
                Skill.builder().id(10L).approvalStatus(Skill.STATUS_APPROVED)
                        .deletedYn("N").createdBy(5L).build()));
        ReputationScore row = service.refreshSkillRequest(1L);
        assertEquals(new BigDecimal("95.00"), row.getScore());
        assertEquals("POPULAR", row.getTier());
        verify(contributorRepository).save(any());
    }

    @Test
    void fiftyVotesAtThirtyDays_scoresFifty_new() {
        stubRequest(request(50, 50, 30, SkillRequest.FULFILLMENT_OPEN), 50);
        ReputationScore row = service.refreshSkillRequest(1L);
        assertEquals(new BigDecimal("50.00"), row.getScore());
        assertEquals("NEW", row.getTier());
    }

    @Test
    void autoConfirm_pastDeadlineApprovedSkill_becomesFulfilled() {
        SkillRequest req = request(40, 40, 0, SkillRequest.FULFILLMENT_PENDING_CONFIRM);
        req.setFulfilledSkillId(10L);
        req.setFulfillmentDeadline(Instant.now().minusSeconds(3600));
        stubRequest(req, 40);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(
                Skill.builder().id(10L).approvalStatus(Skill.STATUS_APPROVED)
                        .deletedYn("N").createdBy(5L).build()));
        when(skillRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReputationScore row = service.refreshSkillRequest(1L);
        assertEquals(SkillRequest.FULFILLMENT_FULFILLED, req.getFulfillmentStatus());
        assertNotNull(req.getFulfilledAt());
        assertEquals(new BigDecimal("95.00"), row.getScore());
        assertEquals("POPULAR", row.getTier());
    }

    @Test
    void autoConfirm_pastDeadlineRejectedSkill_revertsToOpen() {
        SkillRequest req = request(40, 40, 0, SkillRequest.FULFILLMENT_PENDING_CONFIRM);
        req.setFulfilledSkillId(10L);
        req.setFulfillmentDeadline(Instant.now().minusSeconds(3600));
        stubRequest(req, 40);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(
                Skill.builder().id(10L).approvalStatus("REJECTED")
                        .deletedYn("N").createdBy(5L).build()));
        when(skillRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReputationScore row = service.refreshSkillRequest(1L);
        assertEquals(SkillRequest.FULFILLMENT_OPEN, req.getFulfillmentStatus());
        assertNull(req.getFulfilledSkillId());
        assertEquals(new BigDecimal("80.00"), row.getScore());
        assertEquals("TRUSTED", row.getTier());
    }
}

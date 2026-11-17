package com.furelise.skillmanagement.reputation;

import com.furelise.skillmanagement.ainews.repository.AiNewsCommentVoteRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsLikeRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsRepository;
import com.furelise.skillmanagement.ainews.repository.AiNewsViewLogRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolDownloadLogRepository;
import com.furelise.skillmanagement.aitools.repository.AiToolRepository;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.repository.McpDownloadLogRepository;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpRatingRepository;
import com.furelise.skillmanagement.reputation.dto.ReputationExplainResponse;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.model.ReputationScoreId;
import com.furelise.skillmanagement.reputation.repository.ContributorReputationRepository;
import com.furelise.skillmanagement.reputation.repository.InstallEventRepository;
import com.furelise.skillmanagement.reputation.repository.ReputationScoreRepository;
import com.furelise.skillmanagement.reputation.service.impl.ReputationServiceImpl;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillDownloadLogRepository;
import com.furelise.skillmanagement.skill.repository.SkillRatingRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestRepository;
import com.furelise.skillmanagement.skillrequest.repository.SkillRequestVoteRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Install-success rate in the Skill/MCP score (GĐ6, BA §9.1).
 * Fixture: 5 ratings avg 4.9, global mean 4.75 → Bayesian 4.80,
 * 25 downloads → popularity 0.7075. Old blend → 90.95 POPULAR.
 * With >= 5 installs: score = (0.6*bayes/5 + 0.3*wilson + 0.1*pop)*100.
 */
@ExtendWith(MockitoExtension.class)
class ReputationInstallEventsTest {

    @Mock private ReputationScoreRepository scoreRepository;
    @Mock private ContributorReputationRepository contributorRepository;
    @Mock private InstallEventRepository installEventRepository;
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

    private void stubSkill(long installs, long successes) {
        Skill skill = Skill.builder().id(1L).createdBy(null).deletedYn("N").build();
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill));
        when(ratingRepository.countBySkillId(1L)).thenReturn(5L);
        when(ratingRepository.averageRatingBySkillId(1L)).thenReturn(new BigDecimal("4.9"));
        when(ratingRepository.averageRating()).thenReturn(new BigDecimal("4.75"));
        when(downloadLogRepository.countBySkillIdAndUserIdNot(1L, null)).thenReturn(25L);
        when(installEventRepository.countByTargetTypeAndTargetId("SKILL", 1L)).thenReturn(installs);
        when(installEventRepository.countByTargetTypeAndTargetIdAndSuccessTrue("SKILL", 1L))
                .thenReturn(successes);
        when(scoreRepository.findById(any())).thenReturn(Optional.empty());
        when(scoreRepository.save(any(ReputationScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubMcp(long installs, long successes) {
        McpMaster mcp = McpMaster.builder().id(1L).createdBy(null).deletedYn("N").build();
        when(mcpMasterRepository.findById(1L)).thenReturn(Optional.of(mcp));
        when(mcpRatingRepository.countByMcpId(1L)).thenReturn(5L);
        when(mcpRatingRepository.averageRatingByMcpId(1L)).thenReturn(new BigDecimal("4.9"));
        when(mcpRatingRepository.averageRating()).thenReturn(new BigDecimal("4.75"));
        when(mcpDownloadLogRepository.countByMcpIdAndUserIdNot(1L, null)).thenReturn(5L);
        when(installEventRepository.countByTargetTypeAndTargetId("MCP", 1L)).thenReturn(installs);
        when(installEventRepository.countByTargetTypeAndTargetIdAndSuccessTrue("MCP", 1L))
                .thenReturn(successes);
        when(scoreRepository.findById(any())).thenReturn(Optional.empty());
        when(scoreRepository.save(any(ReputationScore.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void noInstallsKeepsOldFormulaAndNullSuccessRate() {
        stubSkill(0, 0);
        ReputationScore row = service.refreshSkill(1L);
        assertEquals(new BigDecimal("90.95"), row.getScore());
        assertEquals("POPULAR", row.getTier());
        assertNull(row.getSuccessRate());
    }

    @Test
    void fewerThanFiveInstallsKeepsOldFormulaButStoresWilson() {
        stubSkill(4, 3);
        ReputationScore row = service.refreshSkill(1L);
        assertEquals(new BigDecimal("90.95"), row.getScore());
        assertEquals("POPULAR", row.getTier());
        assertEquals(new BigDecimal("0.3006"), row.getSuccessRate());
    }

    @Test
    void exactlyFiveInstallsSwitchesToInstallFormula() {
        stubSkill(5, 5);
        ReputationScore row = service.refreshSkill(1L);
        assertEquals(new BigDecimal("81.64"), row.getScore());
        assertEquals("TRUSTED", row.getTier());
        assertEquals(new BigDecimal("0.5655"), row.getSuccessRate());
    }

    @Test
    void tenInstallsNineSuccesses() {
        stubSkill(10, 9);
        ReputationScore row = service.refreshSkill(1L);
        assertEquals(new BigDecimal("82.55"), row.getScore());
        assertEquals("TRUSTED", row.getTier());
        assertEquals(new BigDecimal("0.5958"), row.getSuccessRate());
    }

    @Test
    void mcpUsesSameInstallFormula() {
        stubMcp(10, 8);
        // MCP fixture: 5 downloads → popularity log10(6)/2 = 0.3891.
        // (0.6*0.96 + 0.3*0.4902 + 0.1*0.3891)*100 = 76.20.
        ReputationScore row = service.refreshMcp(1L);
        assertEquals(new BigDecimal("76.20"), row.getScore());
        assertEquals("TRUSTED", row.getTier());
        assertEquals(new BigDecimal("0.4902"), row.getSuccessRate());
    }

    @Test
    void explainShowsInstallSuccessLine() {
        ReputationScore row = ReputationScore.builder()
                .id(new ReputationScoreId("SKILL", 1L))
                .score(new BigDecimal("82.55"))
                .tier("TRUSTED")
                .ratingCount(5)
                .ratingAvg(new BigDecimal("4.80"))
                .downloadCount(25)
                .successRate(new BigDecimal("0.5958"))
                .signals(Map.of("installSuccess", 9L, "installTotal", 10L))
                .build();
        when(scoreRepository.findById(any())).thenReturn(Optional.of(row));
        ReputationExplainResponse explain = service.explain("SKILL", 1L);
        assertTrue(explain.reasons().stream()
                .anyMatch(r -> r.contains("Cài thành công 9/10 lượt (90%)")));
    }

    @Test
    void explainShowsInstallLineEvenBeforeEnoughRatings() {
        ReputationScore row = ReputationScore.builder()
                .id(new ReputationScoreId("SKILL", 1L))
                .score(null)
                .tier("NEW")
                .ratingCount(2)
                .downloadCount(3)
                .signals(Map.of("installSuccess", 6L, "installTotal", 6L))
                .build();
        when(scoreRepository.findById(any())).thenReturn(Optional.of(row));
        ReputationExplainResponse explain = service.explain("SKILL", 1L);
        assertTrue(explain.reasons().stream()
                .anyMatch(r -> r.contains("Cài thành công 6/6 lượt (100%)")));
    }
}

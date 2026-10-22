package com.skillmanagement.skill;

import com.skillmanagement.skill.dto.SkillRatingResponse;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.model.Role;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.model.SkillRating;
import com.skillmanagement.user.model.User;
import com.skillmanagement.skill.repository.SkillRatingRepository;
import com.skillmanagement.common.RateLimiter;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.skill.service.impl.SkillRatingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillRatingServiceTest {

    @Mock private SkillRatingRepository skillRatingRepository;
    @Mock private SkillAccessHelper skillAccessHelper;
    @Mock private RateLimiter rateLimiter;

    @InjectMocks private SkillRatingServiceImpl ratingService;

    private User user;
    private Skill skill;

    @BeforeEach
    void setUp() {
        user = User.builder().id(2L).name("User").email("user@example.com")
                .knoxId("user").role(Role.builder().name("NORMAL_USER").build()).build();
        skill = Skill.builder()
                .id(100L).name("Test Skill")
                .approvalStatus(Skill.STATUS_APPROVED).publicScope(Skill.SCOPE_PUBLIC)
                .createdBy(1L).deletedYn("N").build();
    }

    @Test
    void upsert_invalidStep_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ratingService.upsertRating(100L, user, 4.2));
        assertEquals(BusinessErrorCode.SKILL_INVALID_RATING, ex.getCode());
    }

    @Test
    void upsert_outOfRange_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ratingService.upsertRating(100L, user, 5.5));
        assertEquals(BusinessErrorCode.SKILL_INVALID_RATING, ex.getCode());
    }

    @Test
    void upsert_notPublished_rejected() {
        skill.setApprovalStatus(Skill.STATUS_DRAFT);
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillAccessHelper.getVisible(100L, user)).thenReturn(skill);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ratingService.upsertRating(100L, user, 4.5));
        assertEquals(BusinessErrorCode.SKILL_NOT_APPROVED, ex.getCode());
    }

    @Test
    void upsert_newRating_inserts() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillAccessHelper.getVisible(100L, user)).thenReturn(skill);
        when(skillRatingRepository.findBySkillIdAndUserId(100L, 2L)).thenReturn(Optional.empty());
        when(skillRatingRepository.averageRatingBySkillId(100L)).thenReturn(new BigDecimal("4.5"));
        when(skillRatingRepository.countBySkillId(100L)).thenReturn(1L);

        SkillRatingResponse response = ratingService.upsertRating(100L, user, 4.5);

        verify(skillRatingRepository).save(any(SkillRating.class));
        assertEquals(4.5, response.averageRating());
        assertEquals(1L, response.totalRatings());
    }

    @Test
    void upsert_existingRating_updates() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(skillAccessHelper.getVisible(100L, user)).thenReturn(skill);
        SkillRating existing = SkillRating.builder()
                .id(7L).skillId(100L).userId(2L).rating(new BigDecimal("3.0")).build();
        when(skillRatingRepository.findBySkillIdAndUserId(100L, 2L)).thenReturn(Optional.of(existing));
        when(skillRatingRepository.averageRatingBySkillId(100L)).thenReturn(new BigDecimal("4.5"));
        when(skillRatingRepository.countBySkillId(100L)).thenReturn(1L);

        ratingService.upsertRating(100L, user, 4.5);

        assertEquals(0, new BigDecimal("4.5").compareTo(existing.getRating()));
        verify(skillRatingRepository).save(existing);
    }
}

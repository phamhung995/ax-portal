package com.furelise.skillmanagement.reputation;

import com.furelise.skillmanagement.exception.ResourceNotFoundException;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.reputation.dto.InstallEventRequest;
import com.furelise.skillmanagement.reputation.dto.InstallEventResponse;
import com.furelise.skillmanagement.reputation.dto.ReputationResponse;
import com.furelise.skillmanagement.reputation.model.InstallEvent;
import com.furelise.skillmanagement.reputation.model.ReputationScore;
import com.furelise.skillmanagement.reputation.model.ReputationScoreId;
import com.furelise.skillmanagement.reputation.repository.InstallEventRepository;
import com.furelise.skillmanagement.reputation.service.InstallEventService;
import com.furelise.skillmanagement.reputation.service.ReputationService;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Install-event intake (GĐ6, BA FR-6): dedupe by (target, user, version),
 * knoxId resolution, target validation, score refresh on new events only.
 */
@ExtendWith(MockitoExtension.class)
class InstallEventServiceTest {

    @Mock private InstallEventRepository eventRepository;
    @Mock private ReputationService reputationService;
    @Mock private SkillRepository skillRepository;
    @Mock private McpMasterRepository mcpMasterRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private InstallEventService service;

    private Skill skill() {
        return Skill.builder().id(1L).deletedYn("N").build();
    }

    private ReputationScore refreshed() {
        return ReputationScore.builder()
                .id(new ReputationScoreId("SKILL", 1L))
                .score(new BigDecimal("82.55"))
                .tier("TRUSTED")
                .build();
    }

    @Test
    void newEventIsSavedAndRefreshesScore() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(eventRepository.existsDuplicate("SKILL", 1L, 2L, "1.0.0")).thenReturn(false);
        when(eventRepository.save(any(InstallEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(reputationService.refreshSkill(1L)).thenReturn(refreshed());

        InstallEventResponse response = service.record(
                new InstallEventRequest("skill", 1L, 2L, null, true, "1.0.0"));

        assertFalse(response.duplicate());
        assertEquals(new BigDecimal("82.55"), response.score());
        assertEquals("TRUSTED", response.tier());
        ArgumentCaptor<InstallEvent> captor = ArgumentCaptor.forClass(InstallEvent.class);
        verify(eventRepository).save(captor.capture());
        assertEquals("SKILL", captor.getValue().getTargetType());
        assertEquals(2L, captor.getValue().getUserId());
        assertTrue(captor.getValue().isSuccess());
        assertEquals("1.0.0", captor.getValue().getVersion());
    }

    @Test
    void duplicateEventIsAcknowledgedWithoutSavingOrRefreshing() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(userRepository.existsById(2L)).thenReturn(true);
        when(eventRepository.existsDuplicate("SKILL", 1L, 2L, "1.0.0")).thenReturn(true);
        when(reputationService.getReputation("SKILL", 1L)).thenReturn(
                new ReputationResponse("SKILL", 1L, new BigDecimal("90.95"),
                        "POPULAR", 5, new BigDecimal("4.80"), 25, null));

        InstallEventResponse response = service.record(
                new InstallEventRequest("SKILL", 1L, 2L, null, true, "1.0.0"));

        assertTrue(response.duplicate());
        assertEquals(new BigDecimal("90.95"), response.score());
        assertEquals("POPULAR", response.tier());
        verify(eventRepository, never()).save(any());
        verify(reputationService, never()).refreshSkill(any());
    }

    @Test
    void knoxIdIsResolvedToUser() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(userRepository.findByKnoxIdIgnoreCase("test1"))
                .thenReturn(List.of(User.builder().id(7L).build()));
        when(eventRepository.existsDuplicate("SKILL", 1L, 7L, "1.0.0")).thenReturn(false);
        when(eventRepository.save(any(InstallEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(reputationService.refreshSkill(1L)).thenReturn(refreshed());

        service.record(new InstallEventRequest("SKILL", 1L, null, "test1", true, "1.0.0"));

        ArgumentCaptor<InstallEvent> captor = ArgumentCaptor.forClass(InstallEvent.class);
        verify(eventRepository).save(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
    }

    @Test
    void unknownKnoxIdStoresAnonymousEvent() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(userRepository.findByKnoxIdIgnoreCase("nobody")).thenReturn(List.of());
        when(eventRepository.existsDuplicate("SKILL", 1L, null, "1.0.0")).thenReturn(false);
        when(eventRepository.save(any(InstallEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(reputationService.refreshSkill(1L)).thenReturn(refreshed());

        service.record(new InstallEventRequest("SKILL", 1L, null, "nobody", false, "1.0.0"));

        ArgumentCaptor<InstallEvent> captor = ArgumentCaptor.forClass(InstallEvent.class);
        verify(eventRepository).save(captor.capture());
        assertNull(captor.getValue().getUserId());
        assertFalse(captor.getValue().isSuccess());
    }

    @Test
    void unknownUserIdIsRejected() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(skill()));
        when(userRepository.existsById(99L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.record(
                new InstallEventRequest("SKILL", 1L, 99L, null, true, "1.0.0")));
    }

    @Test
    void unknownTargetTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.record(
                new InstallEventRequest("AI_TOOL", 1L, null, null, true, "1.0.0")));
    }

    @Test
    void missingTargetIs404() {
        when(skillRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.record(
                new InstallEventRequest("SKILL", 999L, null, null, true, "1.0.0")));
    }

    @Test
    void deletedTargetIs404() {
        when(skillRepository.findById(1L)).thenReturn(Optional.of(
                Skill.builder().id(1L).deletedYn("Y").build()));
        assertThrows(ResourceNotFoundException.class, () -> service.record(
                new InstallEventRequest("SKILL", 1L, null, null, true, "1.0.0")));
    }
}

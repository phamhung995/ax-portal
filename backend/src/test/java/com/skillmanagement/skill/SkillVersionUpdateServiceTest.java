package com.skillmanagement.skill;

import com.skillmanagement.mail.EmailService;
import com.skillmanagement.notification.repository.NotificationRepository;
import com.skillmanagement.skill.dto.SkillPendingMetadata;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.model.SkillFile;
import com.skillmanagement.skill.model.SkillSubscription;
import com.skillmanagement.skill.model.SkillVersion;
import com.skillmanagement.skill.model.SkillVersionFile;
import com.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.skillmanagement.skill.repository.SkillFileRepository;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.skillmanagement.skill.repository.SkillVersionFileRepository;
import com.skillmanagement.skill.repository.SkillVersionRepository;
import com.skillmanagement.skill.service.SkillFileStorageService;
import com.skillmanagement.skill.service.SkillVersionUpdateService;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillVersionUpdateServiceTest {

    @Mock private SkillRepository skillRepository;
    @Mock private SkillFileRepository skillFileRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private SkillVersionFileRepository skillVersionFileRepository;
    @Mock private SkillCategoryMappingRepository categoryMappingRepository;
    @Mock private SkillStageMappingRepository stageMappingRepository;
    @Mock private SkillSubscriptionRepository subscriptionRepository;
    @Mock private SkillFileStorageService skillFileStorageService;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private EmailService emailService;

    @InjectMocks private SkillVersionUpdateService service;

    private Skill skill;
    private SkillFile liveFile;
    private SkillFile removedFile;
    private SkillFile pendingFile;

    @BeforeEach
    void setUp() {
        skill = Skill.builder()
                .id(100L).name("Old Name").description("old desc")
                .approvalStatus(Skill.STATUS_PENDING_APPROVAL).publicScope(Skill.SCOPE_PRIVATE)
                .version(1).createdBy(1L).deletedYn("N")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .pendingMetadata(new SkillPendingMetadata(
                        "New Name", "new desc", Skill.SCOPE_PUBLIC,
                        List.of("tag1"), List.of(1L), List.of(2L)))
                .pendingRemoveFileIds(List.of(2L))
                .build();
        liveFile = SkillFile.builder().id(1L).skillId(100L)
                .fileName("keep.txt").filePath("skills/100/keep.txt").fileSize(5L).build();
        removedFile = SkillFile.builder().id(2L).skillId(100L)
                .fileName("remove.txt").filePath("skills/100/remove.txt").fileSize(6L).build();
        pendingFile = SkillFile.builder().id(3L).skillId(100L)
                .fileName("new.txt").filePath("skills/100/new.txt").fileSize(7L)
                .pendingYn("Y").build();
    }

    private void stubFileLookups() {
        when(skillFileRepository.findBySkillIdAndPendingYn(100L, "N"))
                .thenReturn(List.of(liveFile, removedFile));
        when(skillFileRepository.findAllById(List.of(2L))).thenReturn(List.of(removedFile));
    }

    @Test
    void completeVersionUpdate_snapshotsOldStateAppliesNew() {
        stubFileLookups();
        when(skillFileRepository.findBySkillIdAndPendingYn(100L, "Y")).thenReturn(List.of(pendingFile));
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(inv -> {
            SkillVersion v = inv.getArgument(0);
            v.setId(50L);
            return v;
        });
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));

        service.completeVersionUpdate(skill);

        // 1. Snapshot of the superseded state (version 1, old metadata).
        ArgumentCaptor<SkillVersion> versionCaptor = ArgumentCaptor.forClass(SkillVersion.class);
        verify(skillVersionRepository).save(versionCaptor.capture());
        SkillVersion snapshot = versionCaptor.getValue();
        assertEquals(1, snapshot.getVersion());
        assertEquals("Old Name", snapshot.getName());
        assertEquals("old desc", snapshot.getDescription());
        assertEquals(Skill.SCOPE_PRIVATE, snapshot.getPublicScope());

        // Snapshot includes both the kept file and the removed one (the
        // removed file is still live at snapshot time).
        ArgumentCaptor<SkillVersionFile> fileCaptor = ArgumentCaptor.forClass(SkillVersionFile.class);
        verify(skillVersionFileRepository, times(2)).save(fileCaptor.capture());
        List<SkillVersionFile> snapFiles = fileCaptor.getAllValues();
        assertEquals("keep.txt", snapFiles.get(0).getFileName());
        assertEquals("remove.txt", snapFiles.get(1).getFileName());
        assertEquals(50L, snapFiles.get(0).getSkillVersionId());

        // 2. Pending metadata applied to the skill.
        assertEquals("New Name", skill.getName());
        assertEquals("new desc", skill.getDescription());
        assertEquals(Skill.SCOPE_PUBLIC, skill.getPublicScope());
        assertEquals(List.of("tag1"), skill.getHashtags());
        verify(categoryMappingRepository).deleteBySkillId(100L);
        verify(stageMappingRepository).deleteBySkillId(100L);

        // 3. Pending file promoted to live.
        assertEquals("N", pendingFile.getPendingYn());

        // 4. Removed file row deleted (disk NOT deleted).
        verify(skillFileRepository).delete(removedFile);
        verify(skillFileStorageService, never()).delete(anyString());

        // 5. Version bumped, staged state cleared.
        assertEquals(2, skill.getVersion());
        assertNull(skill.getPendingMetadata());
        assertNull(skill.getPendingRemoveFileIds());
        assertNull(skill.getRejectionReason());
    }

    @Test
    void completeVersionUpdate_notifiesSubscribers() {
        stubFileLookups();
        when(skillFileRepository.findBySkillIdAndPendingYn(100L, "Y")).thenReturn(List.of());
        when(skillVersionRepository.save(any(SkillVersion.class))).thenAnswer(inv -> {
            SkillVersion v = inv.getArgument(0);
            v.setId(50L);
            return v;
        });
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        SkillSubscription sub = SkillSubscription.builder().skillId(100L).userId(2L).build();
        when(subscriptionRepository.findBySkillId(100L)).thenReturn(List.of(sub));
        User subscriber = User.builder().id(2L).name("Sub").email("sub@example.com").build();
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(subscriber));
        when(userSettingsRepository.findById(2L)).thenReturn(Optional.empty());

        service.completeVersionUpdate(skill);

        verify(notificationRepository).save(any());
        verify(emailService).sendNotificationEmail(eq("sub@example.com"), anyString(), anyString());
    }

    @Test
    void discardPendingVersion_deletesPendingFilesAndClearsState() {
        when(skillFileRepository.findBySkillIdAndPendingYn(100L, "Y")).thenReturn(List.of(pendingFile));
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));

        service.discardPendingVersion(skill);

        verify(skillFileStorageService).delete("skills/100/new.txt");
        verify(skillFileRepository).delete(pendingFile);
        assertNull(skill.getPendingMetadata());
        assertNull(skill.getPendingRemoveFileIds());
    }
}

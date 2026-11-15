package com.furelise.skillmanagement.skill;

import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.skill.dto.SkillPendingMetadata;
import com.furelise.skillmanagement.skill.model.Skill;
import com.furelise.skillmanagement.skill.model.SkillFile;
import com.furelise.skillmanagement.skill.model.SkillSubscription;
import com.furelise.skillmanagement.skill.model.SkillVersion;
import com.furelise.skillmanagement.skill.model.SkillVersionFile;
import com.furelise.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillFileRepository;
import com.furelise.skillmanagement.skill.repository.SkillRepository;
import com.furelise.skillmanagement.skill.repository.SkillStageMappingRepository;
import com.furelise.skillmanagement.skill.repository.SkillSubscriptionRepository;
import com.furelise.skillmanagement.skill.repository.SkillVersionFileRepository;
import com.furelise.skillmanagement.skill.repository.SkillVersionRepository;
import com.furelise.skillmanagement.skill.service.SkillFileStorageService;
import com.furelise.skillmanagement.skill.service.SkillVersionUpdateService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
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
    @Mock private EventNotificationService eventNotificationService;

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
                        List.of("tag1"), List.of(1L), List.of(2L), null, null))
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

        service.completeVersionUpdate(skill, admin());

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
        User creator = User.builder().id(1L).name("Creator").email("creator@example.com").build();
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(subscriber));
        // The creator is added to the recipients (deduped against subscribers).
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(2L)).thenReturn(Optional.empty());
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        service.completeVersionUpdate(skill, admin());

        verify(eventNotificationService).notify(eq(admin()), eq(subscriber), isNull(), any());
        verify(eventNotificationService).notify(eq(admin()), eq(creator), isNull(), any());
    }

    private User admin() {
        return User.builder().id(3L).name("Admin").email("admin@example.com").build();
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

package com.furelise.skillmanagement.mcp;

import com.furelise.skillmanagement.mcp.dto.McpPendingMetadata;
import com.furelise.skillmanagement.mcp.model.McpFile;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.McpSubscription;
import com.furelise.skillmanagement.mcp.model.McpVersion;
import com.furelise.skillmanagement.mcp.model.McpVersionFile;
import com.furelise.skillmanagement.mcp.repository.McpCategoryMappingRepository;
import com.furelise.skillmanagement.mcp.repository.McpFileRepository;
import com.furelise.skillmanagement.mcp.repository.McpMasterRepository;
import com.furelise.skillmanagement.mcp.repository.McpStageMappingRepository;
import com.furelise.skillmanagement.mcp.repository.McpSubscriptionRepository;
import com.furelise.skillmanagement.mcp.repository.McpVersionFileRepository;
import com.furelise.skillmanagement.mcp.repository.McpVersionRepository;
import com.furelise.skillmanagement.mcp.service.McpFileStorageService;
import com.furelise.skillmanagement.mcp.service.McpVersionUpdateService;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
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
class McpVersionUpdateServiceTest {

    @Mock private McpMasterRepository mcpMasterRepository;
    @Mock private McpFileRepository mcpFileRepository;
    @Mock private McpVersionRepository mcpVersionRepository;
    @Mock private McpVersionFileRepository mcpVersionFileRepository;
    @Mock private McpCategoryMappingRepository categoryMappingRepository;
    @Mock private McpStageMappingRepository stageMappingRepository;
    @Mock private McpSubscriptionRepository subscriptionRepository;
    @Mock private McpFileStorageService mcpFileStorageService;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private EventNotificationService eventNotificationService;

    @InjectMocks private McpVersionUpdateService service;

    private McpMaster mcp;
    private McpFile liveFile;
    private McpFile removedFile;
    private McpFile pendingFile;

    @BeforeEach
    void setUp() {
        mcp = McpMaster.builder()
                .id(100L).mcpCode("MCP-0001").mcpName("Old Name").description("old desc")
                .serverKey("test-mcp").status(McpMaster.STATUS_PENDING_APPROVAL)
                .version(1).createdBy(1L).deletedYn("N")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .pendingMetadata(new McpPendingMetadata(
                        "New Name", "new desc", List.of("tag1"), List.of(1L), List.of(2L)))
                .pendingRemoveFileIds(List.of(2L))
                .build();
        liveFile = McpFile.builder().id(1L).mcpId(100L)
                .fileName("keep.txt").filePath("mcps/100/keep.txt").fileSize(5L).build();
        removedFile = McpFile.builder().id(2L).mcpId(100L)
                .fileName("remove.txt").filePath("mcps/100/remove.txt").fileSize(6L).build();
        pendingFile = McpFile.builder().id(3L).mcpId(100L)
                .fileName("new.txt").filePath("mcps/100/new.txt").fileSize(7L)
                .pendingYn("Y").build();
    }

    private void stubFileLookups() {
        when(mcpFileRepository.findByMcpIdAndPendingYn(100L, "N"))
                .thenReturn(List.of(liveFile, removedFile));
        when(mcpFileRepository.findAllById(List.of(2L))).thenReturn(List.of(removedFile));
    }

    @Test
    void completeVersionUpdate_snapshotsOldStateAppliesNew() {
        stubFileLookups();
        when(mcpFileRepository.findByMcpIdAndPendingYn(100L, "Y")).thenReturn(List.of(pendingFile));
        when(mcpVersionRepository.save(any(McpVersion.class))).thenAnswer(inv -> {
            McpVersion v = inv.getArgument(0);
            v.setId(50L);
            return v;
        });
        when(mcpMasterRepository.save(any(McpMaster.class))).thenAnswer(inv -> inv.getArgument(0));

        service.completeVersionUpdate(mcp, admin());

        // 1. Snapshot of the superseded state (version 1, old metadata).
        ArgumentCaptor<McpVersion> versionCaptor = ArgumentCaptor.forClass(McpVersion.class);
        verify(mcpVersionRepository).save(versionCaptor.capture());
        McpVersion snapshot = versionCaptor.getValue();
        assertEquals(1, snapshot.getVersion());
        assertEquals("Old Name", snapshot.getName());
        assertEquals("old desc", snapshot.getDescription());

        // Snapshot includes both the kept file and the removed one (the
        // removed file is still live at snapshot time).
        ArgumentCaptor<McpVersionFile> fileCaptor = ArgumentCaptor.forClass(McpVersionFile.class);
        verify(mcpVersionFileRepository, times(2)).save(fileCaptor.capture());
        List<McpVersionFile> snapFiles = fileCaptor.getAllValues();
        assertEquals("keep.txt", snapFiles.get(0).getFileName());
        assertEquals("remove.txt", snapFiles.get(1).getFileName());
        assertEquals(50L, snapFiles.get(0).getMcpVersionId());

        // 2. Pending metadata applied to the MCP.
        assertEquals("New Name", mcp.getMcpName());
        assertEquals("new desc", mcp.getDescription());
        assertEquals(List.of("tag1"), mcp.getHashtags());
        verify(categoryMappingRepository).deleteByMcpId(100L);
        verify(stageMappingRepository).deleteByMcpId(100L);

        // 3. Pending file promoted to live.
        assertEquals("N", pendingFile.getPendingYn());

        // 4. Removed file row deleted (disk NOT deleted).
        verify(mcpFileRepository).delete(removedFile);
        verify(mcpFileStorageService, never()).delete(anyString());

        // 5. Version bumped, staged state cleared.
        assertEquals(2, mcp.getVersion());
        assertNull(mcp.getPendingMetadata());
        assertNull(mcp.getPendingRemoveFileIds());
        assertNull(mcp.getRejectionReason());
    }

    @Test
    void completeVersionUpdate_notifiesSubscribers() {
        stubFileLookups();
        when(mcpFileRepository.findByMcpIdAndPendingYn(100L, "Y")).thenReturn(List.of());
        when(mcpVersionRepository.save(any(McpVersion.class))).thenAnswer(inv -> {
            McpVersion v = inv.getArgument(0);
            v.setId(50L);
            return v;
        });
        when(mcpMasterRepository.save(any(McpMaster.class))).thenAnswer(inv -> inv.getArgument(0));
        McpSubscription sub = McpSubscription.builder().mcpId(100L).userId(2L).build();
        when(subscriptionRepository.findByMcpId(100L)).thenReturn(List.of(sub));
        User subscriber = User.builder().id(2L).name("Sub").email("sub@example.com").build();
        User creator = User.builder().id(1L).name("Creator").email("creator@example.com").build();
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(subscriber));
        // The creator is added to the recipients (deduped against subscribers).
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(2L)).thenReturn(Optional.empty());
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.empty());

        service.completeVersionUpdate(mcp, admin());

        verify(eventNotificationService).notify(eq(admin()), eq(subscriber), isNull(), any());
        verify(eventNotificationService).notify(eq(admin()), eq(creator), isNull(), any());
    }

    private User admin() {
        return User.builder().id(3L).name("Admin").email("admin@example.com").build();
    }

    @Test
    void discardPendingVersion_deletesPendingFilesAndClearsState() {
        when(mcpFileRepository.findByMcpIdAndPendingYn(100L, "Y")).thenReturn(List.of(pendingFile));
        when(mcpMasterRepository.save(any(McpMaster.class))).thenAnswer(inv -> inv.getArgument(0));

        service.discardPendingVersion(mcp);

        verify(mcpFileStorageService).delete("mcps/100/new.txt");
        verify(mcpFileRepository).delete(pendingFile);
        assertNull(mcp.getPendingMetadata());
        assertNull(mcp.getPendingRemoveFileIds());
    }
}

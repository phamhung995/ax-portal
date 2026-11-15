package com.furelise.skillmanagement.mcp;

import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.dto.McpPendingMetadata;
import com.furelise.skillmanagement.mcp.model.McpComment;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.repository.McpCommentRepository;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.impl.McpCommentServiceImpl;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.notification.service.EventNotificationService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpCommentServiceTest {

    private static final Long MCP_ID = 10L;
    private static final Long COMMENTER_ID = 1L;
    private static final Long CREATOR_ID = 2L;

    @Mock private McpCommentRepository mcpCommentRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private EventNotificationService eventNotificationService;
    @Mock private McpAccessHelper mcpAccessHelper;
    @Mock private RateLimiter rateLimiter;

    @InjectMocks private McpCommentServiceImpl service;

    private User commenter;
    private McpMaster approvedMcp;

    @BeforeEach
    void setUp() {
        commenter = User.builder()
                .id(COMMENTER_ID).name("Commenter").email("commenter@test.com")
                .password("x").knoxId("commenter")
                .role(Role.builder().id(1L).name("USER").build())
                .build();
        approvedMcp = McpMaster.builder()
                .id(MCP_ID).mcpCode("MCP-0010").mcpName("Approved MCP").serverKey("approved-mcp")
                .status(McpMaster.STATUS_APPROVED).version(1).createdBy(CREATOR_ID).deletedYn("N")
                .build();
    }

    // ── add() ─────────────────────────────────────────────────────────────────

    @Test
    void add_rateLimited_throws() {
        when(rateLimiter.allow("mcp-comment:" + COMMENTER_ID, 30, 60)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.add(MCP_ID, commenter, "hello"));
        assertEquals(BusinessErrorCode.MCP_RATE_LIMITED, ex.getCode());
        verify(mcpCommentRepository, never()).save(any());
    }

    @Test
    void add_draftMcp_throws() {
        McpMaster draft = McpMaster.builder()
                .id(MCP_ID).mcpCode("MCP-0010").mcpName("Draft MCP").serverKey("draft-mcp")
                .status(McpMaster.STATUS_DRAFT).version(1).createdBy(CREATOR_ID).deletedYn("N")
                .build();
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(draft);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.add(MCP_ID, commenter, "hello"));
        assertEquals(BusinessErrorCode.MCP_NOT_APPROVED, ex.getCode());
        verify(mcpCommentRepository, never()).save(any());
    }

    @Test
    void add_versionUpdatePendingMcp_allowed() {
        McpMaster pending = McpMaster.builder()
                .id(MCP_ID).mcpCode("MCP-0010").mcpName("Pending MCP").serverKey("pending-mcp")
                .status(McpMaster.STATUS_PENDING_APPROVAL).version(1).createdBy(CREATOR_ID).deletedYn("N")
                .pendingMetadata(new McpPendingMetadata("Pending v2", null, List.of(), List.of(1L), List.of(1L)))
                .build();
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(pending);

        service.add(MCP_ID, commenter, "hello");

        verify(mcpCommentRepository).save(any(McpComment.class));
    }

    @Test
    void add_emptyContent_throws() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.add(MCP_ID, commenter, "   "));
        assertEquals(BusinessErrorCode.MCP_COMMENT_EMPTY, ex.getCode());
        verify(mcpCommentRepository, never()).save(any());
    }

    @Test
    void add_nullContent_throws() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.add(MCP_ID, commenter, null));
        assertEquals(BusinessErrorCode.MCP_COMMENT_EMPTY, ex.getCode());
    }

    @Test
    void add_tooLong_throws() {
        String longContent = "a".repeat(1001);
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.add(MCP_ID, commenter, longContent));
        assertEquals(BusinessErrorCode.MCP_COMMENT_TOO_LONG, ex.getCode());
        verify(mcpCommentRepository, never()).save(any());
    }

    @Test
    void add_ok_savesAndNotifiesCreator() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);
        User creator = User.builder().id(CREATOR_ID).name("Creator").email("creator@test.com")
                .password("x").knoxId("creator")
                .role(Role.builder().id(1L).name("USER").build()).build();
        when(userRepository.findById(CREATOR_ID)).thenReturn(Optional.of(creator));
        when(userSettingsRepository.findById(CREATOR_ID)).thenReturn(Optional.empty());

        service.add(MCP_ID, commenter, "great mcp!");

        verify(mcpCommentRepository).save(any(McpComment.class));
        verify(eventNotificationService).notify(eq(commenter), eq(creator), isNull(), any());
    }

    @Test
    void add_selfComment_noNotification() {
        McpMaster ownMcp = McpMaster.builder()
                .id(MCP_ID).mcpCode("MCP-0010").mcpName("My MCP").serverKey("my-mcp")
                .status(McpMaster.STATUS_APPROVED).version(1).createdBy(COMMENTER_ID).deletedYn("N")
                .build();
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(ownMcp);

        service.add(MCP_ID, commenter, "note to self");

        verify(mcpCommentRepository).save(any(McpComment.class));
        verifyNoInteractions(eventNotificationService);
    }

    // ── delete() ──────────────────────────────────────────────────────────────

    private McpComment comment(Long id, Long mcpId, Long userId, boolean deleted) {
        McpComment c = McpComment.builder()
                .mcpId(mcpId).userId(userId).content("test").build();
        c.setId(id);
        if (deleted) {
            c.setDeletedAt(Instant.now());
        }
        return c;
    }

    @Test
    void delete_notFound_throws() {
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);
        when(mcpCommentRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(MCP_ID, 99L, commenter));
        assertEquals(BusinessErrorCode.MCP_COMMENT_NOT_FOUND, ex.getCode());
    }

    @Test
    void delete_wrongMcp_throws() {
        McpComment other = comment(1L, 99L, COMMENTER_ID, false);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);
        when(mcpCommentRepository.findById(1L)).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(MCP_ID, 1L, commenter));
        assertEquals(BusinessErrorCode.MCP_COMMENT_NOT_FOUND, ex.getCode());
    }

    @Test
    void delete_alreadyDeleted_throws() {
        McpComment deleted = comment(1L, MCP_ID, COMMENTER_ID, true);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);
        when(mcpCommentRepository.findById(1L)).thenReturn(Optional.of(deleted));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(MCP_ID, 1L, commenter));
        assertEquals(BusinessErrorCode.MCP_COMMENT_NOT_FOUND, ex.getCode());
    }

    @Test
    void delete_byStranger_throws() {
        // Comment owned by CREATOR_ID; commenter (COMMENTER_ID) is not admin.
        McpComment other = comment(1L, MCP_ID, CREATOR_ID, false);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);
        when(mcpCommentRepository.findById(1L)).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(MCP_ID, 1L, commenter));
        assertEquals(BusinessErrorCode.MCP_DELETE_FORBIDDEN, ex.getCode());
    }

    @Test
    void delete_byOwner_softDeletes() {
        McpComment own = comment(1L, MCP_ID, COMMENTER_ID, false);
        when(mcpAccessHelper.getVisible(MCP_ID, commenter)).thenReturn(approvedMcp);
        when(mcpCommentRepository.findById(1L)).thenReturn(Optional.of(own));

        service.delete(MCP_ID, 1L, commenter);

        assertNotNull(own.getDeletedAt());
        verify(mcpCommentRepository).save(own);
    }

    @Test
    void delete_byAdmin_softDeletes() {
        User admin = User.builder().id(3L).name("Admin").email("admin@test.com")
                .password("x").knoxId("admin")
                .role(Role.builder().id(2L).name("ADMIN_USER").build())
                .build();
        McpComment other = comment(1L, MCP_ID, COMMENTER_ID, false); // owned by COMMENTER_ID
        when(mcpAccessHelper.getVisible(MCP_ID, admin)).thenReturn(approvedMcp);
        when(mcpCommentRepository.findById(1L)).thenReturn(Optional.of(other));

        service.delete(MCP_ID, 1L, admin);

        assertNotNull(other.getDeletedAt());
        verify(mcpCommentRepository).save(other);
    }
}

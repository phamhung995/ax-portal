package com.furelise.skillmanagement.mcp;

import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.dto.McpRatingResponse;
import com.furelise.skillmanagement.mcp.model.McpMaster;
import com.furelise.skillmanagement.mcp.model.McpRating;
import com.furelise.skillmanagement.mcp.repository.McpRatingRepository;
import com.furelise.skillmanagement.mcp.service.McpAccessHelper;
import com.furelise.skillmanagement.mcp.service.impl.McpRatingServiceImpl;
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

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpRatingServiceTest {

    @Mock private McpRatingRepository mcpRatingRepository;
    @Mock private McpAccessHelper mcpAccessHelper;
    @Mock private RateLimiter rateLimiter;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private EventNotificationService eventNotificationService;

    @InjectMocks private McpRatingServiceImpl ratingService;

    private User user;
    private McpMaster mcp;

    @BeforeEach
    void setUp() {
        user = User.builder().id(2L).name("User").email("user@example.com")
                .knoxId("user").role(Role.builder().name("NORMAL_USER").build()).build();
        mcp = McpMaster.builder()
                .id(100L).mcpCode("MCP-0001").mcpName("Test MCP").serverKey("test-mcp")
                .status(McpMaster.STATUS_APPROVED).createdBy(1L).deletedYn("N").build();
    }

    @Test
    void upsert_invalidStep_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ratingService.upsertRating(100L, user, 4.2));
        assertEquals(BusinessErrorCode.MCP_INVALID_RATING, ex.getCode());
    }

    @Test
    void upsert_outOfRange_rejected() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ratingService.upsertRating(100L, user, 5.5));
        assertEquals(BusinessErrorCode.MCP_INVALID_RATING, ex.getCode());
    }

    @Test
    void upsert_notApproved_rejected() {
        mcp.setStatus(McpMaster.STATUS_DRAFT);
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(100L, user)).thenReturn(mcp);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ratingService.upsertRating(100L, user, 4.5));
        assertEquals(BusinessErrorCode.MCP_NOT_APPROVED, ex.getCode());
    }

    @Test
    void upsert_versionUpdatePending_allowed() {
        mcp.setStatus(McpMaster.STATUS_PENDING_APPROVAL);
        mcp.setPendingMetadata(new com.furelise.skillmanagement.mcp.dto.McpPendingMetadata(
                "Test MCP v2", null, java.util.List.of(), java.util.List.of(1L), java.util.List.of(1L)));
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(100L, user)).thenReturn(mcp);
        when(mcpRatingRepository.findByMcpIdAndUserId(100L, 2L)).thenReturn(Optional.empty());
        when(mcpRatingRepository.averageRatingByMcpId(100L)).thenReturn(new BigDecimal("4.5"));
        when(mcpRatingRepository.countByMcpId(100L)).thenReturn(1L);

        McpRatingResponse response = ratingService.upsertRating(100L, user, 4.5);

        verify(mcpRatingRepository).save(any(McpRating.class));
        assertEquals(4.5, response.averageRating());
        assertEquals(1L, response.totalRatings());
    }

    @Test
    void upsert_newRating_inserts() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(100L, user)).thenReturn(mcp);
        when(mcpRatingRepository.findByMcpIdAndUserId(100L, 2L)).thenReturn(Optional.empty());
        when(mcpRatingRepository.averageRatingByMcpId(100L)).thenReturn(new BigDecimal("4.5"));
        when(mcpRatingRepository.countByMcpId(100L)).thenReturn(1L);

        McpRatingResponse response = ratingService.upsertRating(100L, user, 4.5);

        verify(mcpRatingRepository).save(any(McpRating.class));
        assertEquals(4.5, response.averageRating());
        assertEquals(1L, response.totalRatings());
    }

    @Test
    void upsert_existingRating_updates() {
        when(rateLimiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mcpAccessHelper.getVisible(100L, user)).thenReturn(mcp);
        McpRating existing = McpRating.builder()
                .id(7L).mcpId(100L).userId(2L).rating(new BigDecimal("3.0")).build();
        when(mcpRatingRepository.findByMcpIdAndUserId(100L, 2L)).thenReturn(Optional.of(existing));
        when(mcpRatingRepository.averageRatingByMcpId(100L)).thenReturn(new BigDecimal("4.5"));
        when(mcpRatingRepository.countByMcpId(100L)).thenReturn(1L);

        ratingService.upsertRating(100L, user, 4.5);

        assertEquals(0, new BigDecimal("4.5").compareTo(existing.getRating()));
        verify(mcpRatingRepository).save(existing);
    }
}

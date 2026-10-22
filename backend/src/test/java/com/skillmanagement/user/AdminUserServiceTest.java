package com.skillmanagement.user;

import com.skillmanagement.user.dto.UpdateUserRequest;
import com.skillmanagement.user.dto.UserSearchRequest;
import com.skillmanagement.user.dto.UserSearchResult;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.model.Role;
import com.skillmanagement.user.model.User;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.user.repository.UserHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.service.impl.AdminUserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserHistoryRepository userHistoryRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AdminUserServiceImpl adminUserService;

    private User target;

    @BeforeEach
    void setUp() {
        target = User.builder()
                .id(10L)
                .name("John Doe")
                .email("john@example.com")
                .knoxId("john")
                .password("encoded")
                .role(Role.builder().name("NORMAL_USER").build())
                .status("ACTIVE")
                .version(3L)
                .build();
    }

    @Test
    void search_returnsPagedItems() {
        Page<User> page = new PageImpl<>(List.of(target));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(organizationRepository.findAll(any(Specification.class))).thenReturn(List.of());

        UserSearchResult result = adminUserService.search(
                new UserSearchRequest("john", null, null, null, null, null, null, null, null, null, 1, 20, "createdDate", "DESC"));

        assertEquals(1, result.totalCount());
        assertEquals("John Doe", result.items().get(0).userName());
        assertTrue(result.items().get(0).orgs().isEmpty());
    }

    @Test
    void updateUser_selfRoleChange_rejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.updateUser(10L,
                        new UpdateUserRequest(null, "ADMIN_USER", null, 3L),
                        "john@example.com", "1.2.3.4"));
        assertEquals(BusinessErrorCode.SELF_ROLE_CHANGE_FORBIDDEN, ex.getCode());
    }

    @Test
    void updateUser_selfStatusChange_rejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.updateUser(10L,
                        new UpdateUserRequest(null, null, "DEACTIVE", 3L),
                        "john@example.com", "1.2.3.4"));
        assertEquals(BusinessErrorCode.SELF_STATUS_CHANGE_FORBIDDEN, ex.getCode());
    }

    @Test
    void updateUser_staleVersion_conflict() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.updateUser(10L,
                        new UpdateUserRequest("New Name", null, null, 2L),
                        "admin@example.com", "1.2.3.4"));
        assertEquals(BusinessErrorCode.CONCURRENT_UPDATE_CONFLICT, ex.getCode());
    }

    @Test
    void updateUser_roleChange_persistsAndWritesHistory() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("ADMIN_USER"))
                .thenReturn(Optional.of(Role.builder().name("ADMIN_USER").build()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(organizationRepository.findByKnoxIdIgnoreCase("john")).thenReturn(List.of());

        var result = adminUserService.updateUser(10L,
                new UpdateUserRequest(null, "ADMIN_USER", null, 3L),
                "admin@example.com", "1.2.3.4");

        assertEquals("ADMIN_USER", result.role());
        verify(userHistoryRepository).save(any());
    }

    @Test
    void updateUser_invalidRole_rejected() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.updateUser(10L,
                        new UpdateUserRequest(null, "SUPERUSER", null, 3L),
                        "admin@example.com", "1.2.3.4"));
        assertEquals(BusinessErrorCode.INVALID_ROLE, ex.getCode());
    }

    @Test
    void updateUser_noChange_noSave() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(target));

        adminUserService.updateUser(10L,
                new UpdateUserRequest("John Doe", "NORMAL_USER", "ACTIVE", 3L),
                "admin@example.com", "1.2.3.4");

        verify(userRepository, never()).save(any());
        verify(userHistoryRepository, never()).save(any());
    }
}

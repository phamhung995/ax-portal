package com.skillmanagement.auth;

import com.skillmanagement.auth.dto.LoginRequest;
import com.skillmanagement.auth.dto.LoginResponse;
import com.skillmanagement.auth.dto.RegisterRequest;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.model.Role;
import com.skillmanagement.user.model.User;
import com.skillmanagement.auth.model.RefreshToken;
import com.skillmanagement.auth.repository.LoginRepository;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.user.repository.UserHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.auth.service.JwtService;
import com.skillmanagement.auth.service.OtpService;
import com.skillmanagement.auth.service.RefreshTokenService;
import com.skillmanagement.auth.service.impl.AuthenticationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserHistoryRepository userHistoryRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private LoginRepository loginRepository;
    @Mock private OtpService otpService;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AuthenticationServiceImpl authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .knoxId("test")
                .password("encoded")
                .role(Role.builder().name("NORMAL_USER").build())
                .status("ACTIVE")
                .build();
    }

    private OrganizationMaster orgRow(String employed) {
        return OrganizationMaster.builder()
                .knoxId("test").userName("Test User").employed(employed)
                .regularPartner("Regular").part("Part A").groupName("Group 1")
                .employeeNumber("123")
                .build();
    }

    @Test
    void login_success_returnsTokensAndOrgs() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Passw0rd!x", "encoded")).thenReturn(true);
        when(organizationRepository.findByKnoxIdIgnoreCase("test")).thenReturn(List.of(orgRow("Y")));
        when(jwtService.generateToken(activeUser)).thenReturn("access-token");
        RefreshToken rt =
                RefreshToken.builder().token("refresh-token").build();
        when(refreshTokenService.createRefreshToken(activeUser)).thenReturn(rt);

        LoginResponse response = authService.login(new LoginRequest("test@example.com", "Passw0rd!x"), "1.2.3.4");

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("NORMAL_USER", response.role());
        assertEquals(1, response.orgs().size());
        assertEquals("Part A", response.orgs().get(0).part());
        assertEquals("Group 1", response.orgs().get(0).group());
        verify(loginRepository).save(argThat(l -> "SUCCESS".equals(l.getLoginResult())));
    }

    @Test
    void login_success_onlyEmployedYRowsInOrgs() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Passw0rd!x", "encoded")).thenReturn(true);
        when(organizationRepository.findByKnoxIdIgnoreCase("test"))
                .thenReturn(List.of(orgRow("Y"), orgRow("N")));
        when(jwtService.generateToken(activeUser)).thenReturn("access-token");
        RefreshToken rt =
                RefreshToken.builder().token("refresh-token").build();
        when(refreshTokenService.createRefreshToken(activeUser)).thenReturn(rt);

        LoginResponse response = authService.login(new LoginRequest("test@example.com", "Passw0rd!x"), "1.2.3.4");

        assertEquals(1, response.orgs().size());
        assertEquals("Group 1", response.orgs().get(0).group());
    }

    @Test
    void login_invalidEmailFormat_throwsInvalidEmail() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("not-an-email", "Passw0rd!x"), "1.2.3.4"));
        assertEquals(BusinessErrorCode.INVALID_EMAIL, ex.getCode());
        verify(loginRepository).save(argThat(l -> "INVALID_EMAIL".equals(l.getFailureReason())));
    }

    @Test
    void login_userNotFound_throwsUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("missing@example.com", "Passw0rd!x"), "1.2.3.4"));
        assertEquals(BusinessErrorCode.USER_NOT_FOUND, ex.getCode());
    }

    @Test
    void login_wrongPassword_throwsInvalidPassword() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("WrongPass1", "encoded")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("test@example.com", "WrongPass1"), "1.2.3.4"));
        assertEquals(BusinessErrorCode.INVALID_PASSWORD, ex.getCode());
    }

    @Test
    void login_deactiveUser_throwsUserDeactive() {
        activeUser.setStatus("DEACTIVE");
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Passw0rd!x", "encoded")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("test@example.com", "Passw0rd!x"), "1.2.3.4"));
        assertEquals(BusinessErrorCode.USER_DEACTIVE, ex.getCode());
    }

    @Test
    void login_noOrgRows_throwsOrganizationNotSynced() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Passw0rd!x", "encoded")).thenReturn(true);
        when(organizationRepository.findByKnoxIdIgnoreCase("test")).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("test@example.com", "Passw0rd!x"), "1.2.3.4"));
        assertEquals(BusinessErrorCode.ORGANIZATION_NOT_SYNCED, ex.getCode());
    }

    @Test
    void login_orgRowsButNoneEmployedY_throwsOrganizationUnavailable() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("Passw0rd!x", "encoded")).thenReturn(true);
        when(organizationRepository.findByKnoxIdIgnoreCase("test")).thenReturn(List.of(orgRow("N")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("test@example.com", "Passw0rd!x"), "1.2.3.4"));
        assertEquals(BusinessErrorCode.ORGANIZATION_UNAVAILABLE, ex.getCode());
    }

    @Test
    void register_requiresVerifiedOtpAndCreatesUser() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("NORMAL_USER"))
                .thenReturn(Optional.of(Role.builder().name("NORMAL_USER").build()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });

        RegisterRequest request = new RegisterRequest("New User", "new@example.com", "Passw0rd!x", "Passw0rd!x");
        assertDoesNotThrow(() -> authService.register(request, "1.2.3.4"));

        verify(otpService).consumeVerifiedOtp("new@example.com", OtpService.EMAIL_VERIFICATION);
        verify(userRepository).save(argThat(u ->
                "ACTIVE".equals(u.getStatus()) && "SELF_REGISTRATION".equals(u.getCreatedAppId())));
        verify(userHistoryRepository).save(any());
    }

    @Test
    void register_passwordMismatch_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(
                        new RegisterRequest("New User", "new@example.com", "Passw0rd!x", "Different1!"), "1.2.3.4"));
        assertEquals(BusinessErrorCode.PASSWORD_MISMATCH, ex.getCode());
    }

    @Test
    void register_weakPassword_throwsPasswordWeak() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(
                        new RegisterRequest("New User", "new@example.com", "alllowercase1", "alllowercase1"), "1.2.3.4"));
        assertEquals(BusinessErrorCode.PASSWORD_WEAK, ex.getCode());
    }
}

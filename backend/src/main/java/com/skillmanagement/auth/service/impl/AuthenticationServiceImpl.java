package com.skillmanagement.auth.service.impl;

import com.skillmanagement.auth.dto.LoginRequest;
import com.skillmanagement.auth.dto.LoginResponse;
import com.skillmanagement.auth.dto.OrgRef;
import com.skillmanagement.auth.dto.RegisterRequest;
import com.skillmanagement.auth.dto.ResetPasswordRequest;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.auth.model.LoginManagement;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.model.Role;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.model.UserHistory;
import com.skillmanagement.auth.repository.LoginRepository;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.user.repository.UserHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.auth.service.AuthenticationService;
import com.skillmanagement.auth.service.JwtService;
import com.skillmanagement.auth.service.OtpService;
import com.skillmanagement.auth.service.PasswordPolicy;
import com.skillmanagement.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Authentication implementation per Login SRS v1.6.
 *
 * Login sequence (SRS §8):
 *   1. Email format
 *   2. User exists
 *   3. Password correct
 *   4. Status != DEACTIVE
 *   5. At least one org row with Employed=Y in organization_master (via knox_id)
 * Every attempt (success + failure) is logged to login_management.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final OrganizationRepository organizationRepository;
    private final LoginRepository loginRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request, String clientIp) {
        String email = request.email() == null ? "" : request.email().trim();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            logLoginFailure(null, email, "INVALID_EMAIL", clientIp);
            throw new BusinessException(BusinessErrorCode.INVALID_EMAIL, "Invalid email format.");
        }
        if (email.length() > 254) {
            logLoginFailure(null, email, "EMAIL_TOO_LONG", clientIp);
            throw new BusinessException(BusinessErrorCode.EMAIL_TOO_LONG, "Email is at most 254 characters.");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> {
                    logLoginFailure(null, email, "USER_NOT_FOUND", clientIp);
                    throw new BusinessException(BusinessErrorCode.USER_NOT_FOUND, "Invalid email or password.");
                });

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            logLoginFailure(user.getId(), email, "INVALID_PASSWORD", clientIp);
            throw new BusinessException(BusinessErrorCode.INVALID_PASSWORD, "Invalid email or password.");
        }

        if ("DEACTIVE".equals(user.getStatus())) {
            logLoginFailure(user.getId(), email, "USER_DEACTIVE", clientIp);
            throw new BusinessException(BusinessErrorCode.USER_DEACTIVE,
                    "This account has been deactivated. Use Forgot Password to reactivate it.");
        }

        // SRS §8 step 5: org check via knox_id on organization_master
        List<OrganizationMaster> orgRows = organizationRepository.findByKnoxIdIgnoreCase(user.getKnoxId());
        boolean hasEmployedY = orgRows.stream().anyMatch(o -> "Y".equals(o.getEmployed()));
        if (!hasEmployedY) {
            // AC-LOGIN-05/06: org rows exist but none employed → UNAVAILABLE; no rows at all → NOT_SYNCED
            logLoginFailure(user.getId(), email,
                    !orgRows.isEmpty() ? "ORGANIZATION_UNAVAILABLE" : "ORGANIZATION_NOT_SYNCED", clientIp);
            throw new BusinessException(!orgRows.isEmpty()
                            ? BusinessErrorCode.ORGANIZATION_UNAVAILABLE
                            : BusinessErrorCode.ORGANIZATION_NOT_SYNCED,
                    "Your organization information is not available. Please contact your administrator.");
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();

        logLoginSuccess(user.getId(), email, clientIp);
        List<OrgRef> orgs = orgRows.stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .map(o -> new OrgRef(o.getPart(), o.getGroupName()))
                .toList();
        return LoginResponse.from(user, orgs, accessToken, refreshToken);
    }

    @Override
    public void requestRegistrationOtp(String email, String clientIp) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // BR-22: do not expose whether the account exists
            throw new BusinessException(BusinessErrorCode.EMAIL_ALREADY_USED,
                    "An account with this email already exists.");
        }
        otpService.requestOtp(email, OtpService.EMAIL_VERIFICATION, null, clientIp);
    }

    @Override
    public void verifyRegistrationOtp(String email, String otp) {
        otpService.verifyOtp(email, OtpService.EMAIL_VERIFICATION, otp);
    }

    @Override
    @Transactional
    public void register(RegisterRequest request, String clientIp) {
        String email = request.email().trim();

        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(BusinessErrorCode.NAME_REQUIRED, "User name cannot be empty.");
        }
        if (request.name().length() > 100) {
            throw new BusinessException(BusinessErrorCode.NAME_TOO_LONG, "User name is at most 100 characters.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(BusinessErrorCode.INVALID_EMAIL, "Invalid email format.");
        }
        if (email.length() > 254) {
            throw new BusinessException(BusinessErrorCode.EMAIL_TOO_LONG, "Email is at most 254 characters.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(BusinessErrorCode.EMAIL_ALREADY_USED, "Email is already in use.");
        }
        PasswordPolicy.validate(request.password());
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_MISMATCH, "Password confirmation mismatch.");
        }
        // BR-13: a verified EMAIL_VERIFICATION OTP (from step 2) is required.
        // OTP mails are sent via Knox Mail API (KnoxMailServiceImpl).
        otpService.consumeVerifiedOtp(email, OtpService.EMAIL_VERIFICATION);

        Role normalRole = roleRepository.findByName("NORMAL_USER")
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.REGISTRATION_FAILED,
                        "NORMAL_USER role is not seeded."));

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(normalRole)
                .status("ACTIVE")
                .createdIp(clientIp)
                .createdAppId("SELF_REGISTRATION")
                .build();
        userRepository.save(user);
        userHistoryRepository.save(snapshot(user));
    }

    @Override
    public void requestPasswordResetOtp(String email, String clientIp) {
        // BR-22: never expose whether the account exists — always the same response.
        if (EMAIL_PATTERN.matcher(email).matches()) {
            User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
            otpService.requestOtp(email, OtpService.PASSWORD_RESET,
                    user != null ? user.getId() : null, clientIp);
        }
    }

    @Override
    public void verifyPasswordResetOtp(String email, String otp) {
        otpService.verifyOtp(email, OtpService.PASSWORD_RESET, otp);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request, String clientIp) {
        String email = request.email().trim();

        PasswordPolicy.validate(request.newPassword());
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_MISMATCH, "Password confirmation mismatch.");
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.USER_NOT_FOUND,
                        "Invalid email or password."));

        if (request.newPassword().equals(user.getPassword())) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_SAME_AS_CURRENT,
                    "New password must be different from current password.");
        }

        // BR-14: verified PASSWORD_RESET OTP (from the verify step) is required
        otpService.consumeVerifiedOtp(email, OtpService.PASSWORD_RESET);

        // BR-19/20: DEACTIVE -> ACTIVE in the same transaction + history snapshot
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setStatus("ACTIVE");
        user.setUpdatedBy(email);
        user.setUpdatedIp(clientIp);
        user.setUpdatedAppId("FORGOT_PASSWORD");
        userRepository.save(user);
        userHistoryRepository.save(snapshot(user));
    }

    private void logLoginSuccess(Long userId, String email, String clientIp) {
        loginRepository.save(LoginManagement.builder()
                .userId(userId).email(email).loginIp(clientIp)
                .loginResult("SUCCESS").appId("LOGIN").build());
    }

    private void logLoginFailure(Long userId, String email, String reason, String clientIp) {
        loginRepository.save(LoginManagement.builder()
                .userId(userId).email(email).loginIp(clientIp)
                .loginResult("FAILURE").failureReason(reason).appId("LOGIN").build());
    }

    private UserHistory snapshot(User user) {
        return UserHistory.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .status(user.getStatus())
                .registerId(user.getRegisterId())
                .createdIp(user.getCreatedIp())
                .createdAppId(user.getCreatedAppId())
                .updatedIp(user.getUpdatedIp())
                .updatedAppId(user.getUpdatedAppId())
                .updatedBy(user.getUpdatedBy())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

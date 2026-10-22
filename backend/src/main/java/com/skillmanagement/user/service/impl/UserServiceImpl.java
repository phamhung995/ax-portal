package com.skillmanagement.user.service.impl;

import com.skillmanagement.auth.dto.ChangePasswordRequest;
import com.skillmanagement.auth.dto.MeResponse;
import com.skillmanagement.auth.dto.OrgRef;
import com.skillmanagement.notification.dto.NotificationsRequest;
import com.skillmanagement.user.dto.*;
import com.skillmanagement.auth.dto.*;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.skill.dto.SearchUserItem;
import com.skillmanagement.skill.dto.SearchUserResult;
import com.skillmanagement.user.dto.PreferencesRequest;
import com.skillmanagement.user.dto.ProfileUpdateRequest;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.model.UserHistory;
import com.skillmanagement.user.model.UserSettings;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.user.repository.UserHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.repository.UserSettingsRepository;
import com.skillmanagement.user.service.FileStorageService;
import com.skillmanagement.auth.service.PasswordPolicy;
import com.skillmanagement.common.RateLimiter;
import com.skillmanagement.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Settings implementation (Settings SRS).
 * Email is immutable — the profile payload only carries displayName (+ avatar).
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> ALLOWED_LANGUAGES = Set.of("en", "vi");
    private static final Set<String> ALLOWED_DATE_FORMATS = Set.of("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD");
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024; // 2 MB
    private static final int PASSWORD_CHANGE_MAX_ATTEMPTS = 5;
    private static final int PASSWORD_CHANGE_WINDOW_SECONDS = 15 * 60;

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final OrganizationRepository organizationRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;

    @Override
    @Transactional
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.USER_NOT_FOUND_404, "User not found."));
        return toMe(user);
    }

    @Override
    @Transactional
    public MeResponse updateProfile(User user, ProfileUpdateRequest request,
                                    MultipartFile avatar, boolean removeAvatar) {
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new BusinessException(BusinessErrorCode.NAME_REQUIRED, "Display name cannot be empty.");
        }
        if (request.displayName().length() > 50) {
            throw new BusinessException(BusinessErrorCode.NAME_TOO_LONG, "Display name is at most 50 characters.");
        }
        user.setName(request.displayName().trim());

        if (removeAvatar) {
            if (user.getAvatarUrl() != null) {
                fileStorageService.deleteAvatar(user.getAvatarUrl());
            }
            user.setAvatarUrl(null);
        } else if (avatar != null && !avatar.isEmpty()) {
            if (avatar.getSize() > MAX_AVATAR_SIZE) {
                throw new BusinessException(BusinessErrorCode.AVATAR_TOO_LARGE, "Image size is at most 2MB.");
            }
            if (!ALLOWED_AVATAR_TYPES.contains(avatar.getContentType())) {
                throw new BusinessException(BusinessErrorCode.AVATAR_INVALID_TYPE, "Image format is not supported.");
            }
            String newUrl = fileStorageService.storeAvatar(user.getId(), avatar, user.getAvatarUrl());
            user.setAvatarUrl(newUrl);
        }

        userRepository.save(user);
        userHistoryRepository.save(snapshot(user));
        return toMe(user);
    }

    @Override
    @Transactional
    public MeResponse updatePreferences(Long userId, PreferencesRequest request) {
        if (!ALLOWED_LANGUAGES.contains(request.language())) {
            throw new BusinessException(BusinessErrorCode.INVALID_LANGUAGE, "Invalid language.");
        }
        if (request.timezone() == null || request.timezone().isBlank()
                || !request.timezone().matches("^[A-Za-z_]+/[A-Za-z_]+(/[A-Za-z_]+)?$")) {
            throw new BusinessException(BusinessErrorCode.INVALID_TIMEZONE, "Invalid timezone.");
        }
        if (!ALLOWED_DATE_FORMATS.contains(request.dateFormat())) {
            throw new BusinessException(BusinessErrorCode.INVALID_DATE_FORMAT, "Invalid date format.");
        }

        UserSettings settings = loadOrCreateSettings(userId);
        settings.setLanguage(request.language());
        settings.setTimezone(request.timezone());
        settings.setDateFormat(request.dateFormat());
        userSettingsRepository.save(settings);
        return toMe(userRepository.findById(userId).orElseThrow());
    }

    @Override
    @Transactional
    public MeResponse updateNotifications(Long userId, NotificationsRequest request) {
        UserSettings settings = loadOrCreateSettings(userId);
        settings.setEmailNotify(request.emailNotify());
        settings.setInappNotify(request.inappNotify());
        userSettingsRepository.save(settings);
        return toMe(userRepository.findById(userId).orElseThrow());
    }

    @Override
    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!rateLimiter.allow("password-change:" + user.getId(),
                PASSWORD_CHANGE_MAX_ATTEMPTS, PASSWORD_CHANGE_WINDOW_SECONDS)) {
            throw new BusinessException(BusinessErrorCode.TOO_MANY_ATTEMPTS,
                    "Too many attempts. Please try again later.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_CURRENT_WRONG,
                    "Current password is incorrect.");
        }
        PasswordPolicy.validate(request.newPassword());
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_SAME_AS_CURRENT,
                    "New password must be different from current password.");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_MISMATCH,
                    "Password confirmation mismatch.");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        userHistoryRepository.save(snapshot(user));
    }

    @Override
    @Transactional(readOnly = true)
    public SearchUserResult searchForSkill(String keyword, int page, int pageSize) {
        int p = page < 1 ? 1 : page;
        int size = pageSize < 1 ? 20 : pageSize;
        Pageable pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Direction.ASC, "name"));

        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Specification<User> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));
            if (!kw.isEmpty()) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), "%" + kw + "%"),
                        cb.like(cb.lower(root.get("knoxId")), "%" + kw + "%")));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<User> result = userRepository.findAll(spec, pageable);
        List<User> users = result.getContent();
        Map<String, List<OrgRef>> orgsByKnox = users.stream()
                .map(User::getKnoxId).distinct()
                .collect(Collectors.toMap(
                        k -> k.toLowerCase(Locale.ROOT),
                        k -> organizationRepository.findByKnoxIdIgnoreCase(k).stream()
                                .filter(o -> "Y".equals(o.getEmployed()))
                                .map(o -> new OrgRef(o.getPart(), o.getGroupName()))
                                .toList()));

        List<SearchUserItem> items = users.stream().map(u -> new SearchUserItem(
                u.getId(), u.getName(), u.getKnoxId(),
                orgsByKnox.getOrDefault(u.getKnoxId().toLowerCase(Locale.ROOT), List.of())))
                .toList();
        return new SearchUserResult(result.getTotalElements(), p, size, items);
    }

    // ------------------------------------------------------------------

    private UserSettings loadOrCreateSettings(Long userId) {
        return userSettingsRepository.findById(userId)
                .orElseGet(() -> userSettingsRepository.save(UserSettings.builder().userId(userId).build()));
    }

    private MeResponse toMe(User user) {
        UserSettings settings = userSettingsRepository.findById(user.getId())
                .orElse(UserSettings.builder().userId(user.getId())
                        .language("en").timezone("Asia/Ho_Chi_Minh")
                        .dateFormat("DD/MM/YYYY").emailNotify(true).inappNotify(true).build());
        List<OrgRef> orgs = organizationRepository.findByKnoxIdIgnoreCase(user.getKnoxId()).stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .map(o -> new OrgRef(o.getPart(), o.getGroupName()))
                .toList();
        return new MeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                orgs,
                new MeResponse.SettingsBlock(
                        settings.getLanguage(), settings.getTimezone(), settings.getDateFormat(),
                        settings.isEmailNotify(), settings.isInappNotify())
        );
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

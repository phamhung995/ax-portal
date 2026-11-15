package com.furelise.skillmanagement.user.service;

import com.furelise.skillmanagement.notification.dto.NotificationsRequest;
import com.furelise.skillmanagement.user.dto.*;
import com.furelise.skillmanagement.auth.dto.*;
import com.furelise.skillmanagement.skill.dto.*;
import com.furelise.skillmanagement.user.model.User;
import org.springframework.web.multipart.MultipartFile;

/**
 * User Settings (Settings SRS): profile, preferences, notifications, password.
 * Email is immutable (Login SRS v1.6 decision).
 */
public interface UserService {

    MeResponse getMe(Long userId);

    MeResponse updateProfile(User user, ProfileUpdateRequest request,
                             MultipartFile avatar, boolean removeAvatar);

    MeResponse updatePreferences(Long userId, PreferencesRequest request);

    MeResponse updateNotifications(Long userId, NotificationsRequest request);

    void changePassword(User user, ChangePasswordRequest request);

    /**
     * Search users for the Skill grid Creator field (Skill SRS §7.4):
     * name or Knox ID contains the keyword (case-insensitive), ACTIVE only.
     */
    SearchUserResult searchForSkill(String keyword, int page, int pageSize);
}

package com.skillmanagement.user.service;

import com.skillmanagement.auth.dto.ChangePasswordRequest;
import com.skillmanagement.auth.dto.MeResponse;
import com.skillmanagement.notification.dto.NotificationsRequest;
import com.skillmanagement.user.dto.*;
import com.skillmanagement.auth.dto.*;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.skill.dto.SearchUserResult;
import com.skillmanagement.user.dto.PreferencesRequest;
import com.skillmanagement.user.dto.ProfileUpdateRequest;
import com.skillmanagement.user.model.User;
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

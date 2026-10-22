package com.skillmanagement.user.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service for managing file storage (such as user avatars).
 */
public interface FileStorageService {
    String storeAvatar(Long userId, MultipartFile file, String oldAvatarUrl);
    void deleteAvatar(String avatarUrl);
}

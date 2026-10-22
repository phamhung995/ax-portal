package com.skillmanagement.user.service.impl;

import com.skillmanagement.user.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

/**
 * Implementation of FileStorageService storing files to local disk.
 */
@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private final Path uploadDir;
    private final String avatarUrlPrefix;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    public FileStorageServiceImpl(@Value("${app.upload.dir:uploads/avatars/}") String uploadDirPath,
                                  @Value("${app.upload.avatar-url-prefix:/uploads/avatars}") String avatarUrlPrefix) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        this.avatarUrlPrefix = avatarUrlPrefix.endsWith("/")
                ? avatarUrlPrefix.substring(0, avatarUrlPrefix.length() - 1)
                : avatarUrlPrefix;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục lưu trữ file: " + uploadDirPath, e);
        }
    }

    @Override
    public String storeAvatar(Long userId, MultipartFile file, String oldAvatarUrl) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File upload không được để trống.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Dung lượng ảnh tối đa là 5MB.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Định dạng file không hợp lệ. Chỉ chấp nhận JPG, PNG, WEBP.");
        }

        // Xóa file cũ nếu có
        deleteAvatar(oldAvatarUrl);

        String newFilename = "avatar_" + userId + "_" + System.currentTimeMillis() + "." + extension;
        Path targetPath = this.uploadDir.resolve(newFilename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Không thể lưu file avatar.", e);
        }

        return avatarUrlPrefix + "/" + newFilename;
    }

    @Override
    public void deleteAvatar(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(avatarUrlPrefix + "/")) {
            return;
        }
        String filename = avatarUrl.substring((avatarUrlPrefix + "/").length());
        Path filePath = this.uploadDir.resolve(filename);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Không thể xóa file avatar cũ: {}", filePath, e);
        }
    }
}

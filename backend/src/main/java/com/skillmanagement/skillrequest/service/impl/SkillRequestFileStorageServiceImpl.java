package com.skillmanagement.skillrequest.service.impl;

import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.skillrequest.service.SkillRequestFileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Disk storage for skill request attachments under
 * {@code uploads/skill-requests/{requestId}/}.
 * Images render in the post's image grid; other files appear as download
 * links. The original file name is kept in skill_request_files.file_name.
 */
@Slf4j
@Service
public class SkillRequestFileStorageServiceImpl implements SkillRequestFileStorageService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // images
            "jpg", "jpeg", "png", "webp", "gif",
            // documents & archives
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "7z", "txt", "md", "json", "csv", "log", "yaml", "yml", "py", "js", "ts");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    private final Path requestDir;

    public SkillRequestFileStorageServiceImpl(
            @Value("${app.upload.skill-request-dir:uploads/skill-requests/}") String requestDirPath) {
        this.requestDir = Paths.get(requestDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.requestDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục lưu trữ skill request file: " + requestDirPath, e);
        }
    }

    @Override
    public String store(Long requestId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILE_INVALID_TYPE,
                    "File format is not supported.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILE_TOO_LARGE,
                    "File size is at most 10MB.");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILE_INVALID_TYPE,
                    "File format is not supported.");
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path targetDir = requestDir.resolve(String.valueOf(requestId));
        Path target = targetDir.resolve(storedName);
        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Không thể lưu skill request file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to store the skill request file.");
        }
        return requestId + "/" + storedName;
    }

    @Override
    public void delete(String relativePath) {
        Path target = resolve(relativePath);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Không thể xóa skill request file: {}", target, e);
        }
    }

    @Override
    public InputStream load(String relativePath) {
        Path target = resolve(relativePath);
        if (target == null || !Files.isRegularFile(target)) {
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILE_NOT_FOUND,
                    "Skill request file does not exist.");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("Không thể đọc skill request file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.SKILL_REQUEST_FILE_NOT_FOUND,
                    "Skill request file does not exist.");
        }
    }

    @Override
    public boolean isImage(String fileName) {
        if (fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return IMAGE_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Resolves a relative path inside the request dir, guarding against traversal. */
    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path candidate = requestDir.resolve(relativePath).normalize();
        if (!candidate.startsWith(requestDir)) {
            log.warn("Từ chối truy cập skill request file ngoài thư mục lưu trữ: {}", relativePath);
            return null;
        }
        return candidate;
    }
}

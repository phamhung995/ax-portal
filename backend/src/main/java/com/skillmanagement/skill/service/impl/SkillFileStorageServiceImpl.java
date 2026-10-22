package com.skillmanagement.skill.service.impl;

import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.skill.service.SkillFileStorageService;
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
 * Disk storage for skill files under {@code uploads/skills/{skillId}/}.
 * Stored file names are UUID-prefixed to avoid name collisions and path
 * traversal; the original file name is kept in skills_master.file_name.
 */
@Slf4j
@Service
public class SkillFileStorageServiceImpl implements SkillFileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "zip", "7z", "json", "yaml", "yml", "txt", "md", "py", "js", "ts");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    private final Path skillDir;

    public SkillFileStorageServiceImpl(@Value("${app.upload.skill-dir:uploads/skills/}") String skillDirPath) {
        this.skillDir = Paths.get(skillDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.skillDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục lưu trữ skill file: " + skillDirPath, e);
        }
    }

    @Override
    public String store(Long skillId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_INVALID_TYPE,
                    "File format is not supported.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_TOO_LARGE,
                    "File size is at most 10MB.");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_INVALID_TYPE,
                    "File format is not supported.");
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path targetDir = skillDir.resolve(String.valueOf(skillId));
        Path target = targetDir.resolve(storedName);
        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Không thể lưu skill file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to store the skill file.");
        }
        return skillId + "/" + storedName;
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
            log.warn("Không thể xóa skill file: {}", target, e);
        }
    }

    @Override
    public InputStream load(String relativePath) {
        Path target = resolve(relativePath);
        if (target == null || !Files.isRegularFile(target)) {
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                    "Skill file does not exist.");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("Không thể đọc skill file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.SKILL_FILE_NOT_FOUND,
                    "Skill file does not exist.");
        }
    }

    /** Resolves a relative path inside the skill dir, guarding against traversal. */
    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path candidate = skillDir.resolve(relativePath).normalize();
        if (!candidate.startsWith(skillDir)) {
            log.warn("Từ chối truy cập skill file ngoài thư mục lưu trữ: {}", relativePath);
            return null;
        }
        return candidate;
    }
}

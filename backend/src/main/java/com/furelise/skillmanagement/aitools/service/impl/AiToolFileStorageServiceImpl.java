package com.furelise.skillmanagement.aitools.service.impl;

import com.furelise.skillmanagement.aitools.service.AiToolFileStorageService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
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
 * Disk storage for AI tool files under {@code uploads/ai-tools/{toolId}/}.
 * Stored file names are UUID-prefixed to avoid name collisions and path
 * traversal; the original file name is kept in ai_tool_files.file_name.
 */
@Slf4j
@Service
public class AiToolFileStorageServiceImpl implements AiToolFileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "zip", "7z", "json", "yaml", "yml", "txt", "md", "py", "js", "ts");
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private final Path aiToolDir;

    public AiToolFileStorageServiceImpl(@Value("${app.upload.ai-tool-dir:uploads/ai-tools/}") String aiToolDirPath) {
        this.aiToolDir = Paths.get(aiToolDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.aiToolDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục lưu trữ AI tool file: " + aiToolDirPath, e);
        }
    }

    @Override
    public String store(Long toolId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_FILE_INVALID_TYPE,
                    "File format is not supported.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_FILE_TOO_LARGE,
                    "File size is at most 50MB.");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_FILE_INVALID_TYPE,
                    "File format is not supported.");
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path targetDir = aiToolDir.resolve(String.valueOf(toolId));
        Path target = targetDir.resolve(storedName);
        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Không thể lưu AI tool file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to store the AI tool file.");
        }
        return toolId + "/" + storedName;
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
            log.warn("Không thể xóa AI tool file: {}", target, e);
        }
    }

    @Override
    public InputStream load(String relativePath) {
        Path target = resolve(relativePath);
        if (target == null || !Files.isRegularFile(target)) {
            throw new BusinessException(BusinessErrorCode.AI_TOOL_FILE_NOT_FOUND,
                    "AI tool file does not exist.");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("Không thể đọc AI tool file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.AI_TOOL_FILE_NOT_FOUND,
                    "AI tool file does not exist.");
        }
    }

    /** Resolves a relative path inside the AI tool dir, guarding against traversal. */
    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path candidate = aiToolDir.resolve(relativePath).normalize();
        if (!candidate.startsWith(aiToolDir)) {
            log.warn("Từ chối truy cập AI tool file ngoài thư mục lưu trữ: {}", relativePath);
            return null;
        }
        return candidate;
    }
}

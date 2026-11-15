package com.furelise.skillmanagement.mcp.service.impl;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.mcp.service.McpFileStorageService;
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
 * Disk storage for MCP files under {@code uploads/mcps/{mcpId}/}.
 * Stored file names are UUID-prefixed to avoid name collisions and path
 * traversal; the original file name is kept in mcp_files.file_name.
 */
@Slf4j
@Service
public class McpFileStorageServiceImpl implements McpFileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "zip", "7z", "json", "yaml", "yml", "txt", "md", "py", "js", "ts");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    private final Path mcpDir;

    public McpFileStorageServiceImpl(@Value("${app.upload.mcp-dir:uploads/mcps/}") String mcpDirPath) {
        this.mcpDir = Paths.get(mcpDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.mcpDir);
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo thư mục lưu trữ MCP file: " + mcpDirPath, e);
        }
    }

    @Override
    public String store(Long mcpId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_INVALID_TYPE,
                    "File format is not supported.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_TOO_LARGE,
                    "File size is at most 10MB.");
        }
        String originalFilename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            extension = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_INVALID_TYPE,
                    "File format is not supported.");
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path targetDir = mcpDir.resolve(String.valueOf(mcpId));
        Path target = targetDir.resolve(storedName);
        try {
            Files.createDirectories(targetDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Không thể lưu MCP file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to store the MCP file.");
        }
        return mcpId + "/" + storedName;
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
            log.warn("Không thể xóa MCP file: {}", target, e);
        }
    }

    @Override
    public InputStream load(String relativePath) {
        Path target = resolve(relativePath);
        if (target == null || !Files.isRegularFile(target)) {
            throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                    "MCP file does not exist.");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            log.error("Không thể đọc MCP file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.MCP_FILE_NOT_FOUND,
                    "MCP file does not exist.");
        }
    }

    /** Resolves a relative path inside the MCP dir, guarding against traversal. */
    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path candidate = mcpDir.resolve(relativePath).normalize();
        if (!candidate.startsWith(mcpDir)) {
            log.warn("Từ chối truy cập MCP file ngoài thư mục lưu trữ: {}", relativePath);
            return null;
        }
        return candidate;
    }
}

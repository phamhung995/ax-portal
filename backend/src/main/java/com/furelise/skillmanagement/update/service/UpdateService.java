package com.furelise.skillmanagement.update.service;

import com.furelise.skillmanagement.update.dto.UpdateManifest;
import com.furelise.skillmanagement.update.model.AppRelease;
import com.furelise.skillmanagement.update.repository.AppReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateService {

    private final AppReleaseRepository repository;

    @Value("${app.update.storage-dir:uploads/app-releases}")
    private String storageDir;

    @Value("${app.update.public-base-url:http://localhost:8080/api/v1/update}")
    private String publicBaseUrl;

    /**
     * Latest active release manifest (tauri-plugin-updater format).
     * Returns null if no active release.
     */
    public UpdateManifest getLatestManifest() {
        return repository.findFirstByIsActiveTrueOrderByPublishedAtDesc()
                .map(this::toManifest)
                .orElse(null);
    }

    /**
     * Build manifest for a release — constructs download URL from publicBaseUrl.
     */
    private UpdateManifest toManifest(AppRelease r) {
        String downloadUrl = publicBaseUrl + "/download/" + r.getVersion();
        String pubDate = r.getPublishedAt() != null
                ? r.getPublishedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                : null;
        return new UpdateManifest(
                r.getVersion(),
                r.getReleaseNotes(),
                pubDate,
                Map.of("windows-x86_64",
                        new UpdateManifest.PlatformAsset(r.getSignature(), downloadUrl))
        );
    }

    /**
     * Find release by version (active only). Returns null if not found.
     */
    public AppRelease getByVersion(String version) {
        return repository.findByVersionAndIsActiveTrue(version).orElse(null);
    }

    /**
     * Resource for download streaming. Returns null if file missing on disk.
     */
    public Resource getReleaseResource(AppRelease release) {
        Path path = Paths.get(release.getNsisZipPath());
        if (!Files.isRegularFile(path)) {
            log.warn("Release file not on disk: {}", path);
            return null;
        }
        return new FileSystemResource(path);
    }

    /**
     * All active releases, newest first.
     */
    public List<AppRelease> listAll() {
        return repository.findAllByIsActiveTrueOrderByPublishedAtDesc();
    }

    /**
     * Upload new release — stores .nsis.zip on disk, saves metadata to DB.
     * If version already exists, overwrites file + metadata.
     */
    @Transactional
    public AppRelease uploadRelease(String version, String signature, String notes,
                                   MultipartFile nsisZip) throws IOException {
        Path dir = Paths.get(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String fileName = nsisZip.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "AIPRO_STUDIO_" + version + "_x64-setup.nsis.zip";
        }
        Path dest = dir.resolve(fileName);
        Files.copy(nsisZip.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        long size = Files.size(dest);

        AppRelease release = repository.findByVersion(version)
                .orElseGet(AppRelease::new);
        release.setVersion(version);
        release.setNsisZipName(fileName);
        release.setNsisZipPath(dest.toString());
        release.setNsisZipSize(size);
        release.setSignature(signature);
        release.setReleaseNotes(notes);
        release.setActive(true);
        return repository.save(release);
    }

    /**
     * Soft-delete release (set inactive). Returns true if found.
     */
    @Transactional
    public boolean deactivateRelease(String version) {
        return repository.findByVersionAndIsActiveTrue(version)
                .map(r -> {
                    r.setActive(false);
                    repository.save(r);
                    return true;
                })
                .orElse(false);
    }
}

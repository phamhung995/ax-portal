package com.furelise.skillmanagement.update.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * App release for AIPRO STUDIO silent auto-update (tauri-plugin-updater).
 * Stores signed .nsis.zip + signature + metadata served via public API.
 */
@Entity
@Table(name = "app_releases")
@Getter
@Setter
@NoArgsConstructor
public class AppRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String version;

    @Column(nullable = false, length = 16)
    private String channel = "stable";

    @Column(name = "nsis_zip_name", nullable = false, length = 255)
    private String nsisZipName;

    @Column(name = "nsis_zip_path", nullable = false, length = 512)
    private String nsisZipPath;

    @Column(name = "nsis_zip_size", nullable = false)
    private long nsisZipSize;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String signature;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt = OffsetDateTime.now();

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}

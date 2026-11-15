package com.furelise.skillmanagement.update.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Manifest format for tauri-plugin-updater.
 * Plugin GETs this from the endpoint, parses version + platforms.windows-x86_64,
 * downloads the .nsis.zip from `url`, verifies `signature`, installs passive.
 *
 * Example:
 * {
 *   "version": "1.20.20",
 *   "notes": "release notes",
 *   "pub_date": "2026-11-09T08:00:00Z",
 *   "platforms": {
 *     "windows-x86_64": {
 *       "signature": "<.sig file content>",
 *       "url": "http://host/api/v1/update/download/1.20.20"
 *     }
 *   }
 * }
 */
public record UpdateManifest(
        String version,
        String notes,
        @JsonProperty("pub_date") String pubDate,
        Map<String, PlatformAsset> platforms
) {
    public record PlatformAsset(String signature, String url) {}
}

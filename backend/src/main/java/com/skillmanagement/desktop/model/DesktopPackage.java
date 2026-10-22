package com.skillmanagement.desktop.model;

import java.util.UUID;

/** Internal storage identity is deliberately absent from the HTTP DTO. */
public record DesktopPackage(UUID id,String format,String fileName,long sizeBytes,String sha256,UUID storageKey) {}

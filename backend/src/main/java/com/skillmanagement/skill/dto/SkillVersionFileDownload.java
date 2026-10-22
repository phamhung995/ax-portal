package com.skillmanagement.skill.dto;

import java.io.InputStream;

/**
 * A snapshotted version file ready to stream to the client.
 */
public record SkillVersionFileDownload(
        String fileName,
        InputStream stream
) {}

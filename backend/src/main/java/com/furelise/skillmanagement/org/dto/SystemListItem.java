package com.furelise.skillmanagement.org.dto;

/**
 * One row of the Systems tab grid: a system joined with one of its sub
 * systems. Systems without any sub system appear once with blank
 * subSystemCode / subSystemName.
 */
public record SystemListItem(
        String subSystemCode,
        String subSystemName,
        String systemKey,
        String systemName,
        String currentPhase,
        String clientOfSds,
        String gdcGroup,
        String gdcPart,
        String gdcTpmMain,
        String projectKey,
        String projectName,
        String subProjectKey
) {
}

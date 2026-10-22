package com.skillmanagement.desktop.dto;

import java.time.Instant;
import java.util.List;

public final class DesktopDtos {
    private DesktopDtos() {}
    public record UserView(String id,String displayName) {}
    public record Source(String id,String label) {}
    public record Mode(String id,String label,boolean available) {}
    public record Taxonomy(List<Source> sources,List<Mode> catalogModes) {}
    public record Page<T>(List<T> items,int page,int pageSize,long totalItems) {}
    public record ReleaseRef(String id,long sequence,String version) {}
    public record PackageInfo(String format,String fileName,long sizeBytes,String sha256,String downloadPath) {}
    public record Release(String skillId,String id,long sequence,String version,String installName,Instant publishedAt,String changelog,List<String> compatibleAgents,List<String> requiredMcps,@com.fasterxml.jackson.annotation.JsonProperty("package") PackageInfo artifact) {
        public ReleaseRef reference() { return new ReleaseRef(id,sequence,version); }
    }
    public record SkillSummary(String id,String name,String description,long metadataRevision,
        List<String> roles,List<String> tags,List<String> categoryIds,List<String> sourceIds,
        UserView creator,List<String> compatibleAgents,boolean canDownload,Instant updatedAt,
        ReleaseRef latestRelease) {}
}

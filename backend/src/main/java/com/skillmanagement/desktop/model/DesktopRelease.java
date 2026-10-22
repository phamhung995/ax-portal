package com.skillmanagement.desktop.model;

import java.time.Instant;
import java.util.List;

public record DesktopRelease(String skillId,String id,long sequence,String version,String installName,
    Instant publishedAt,String changelog,List<String> compatibleAgents,List<String> requiredMcps) {}

package com.skillmanagement.desktop.updates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.skillmanagement.desktop.dto.DesktopDtos;
import com.skillmanagement.desktop.service.DesktopFailure;

import java.time.Instant;
import java.util.*;

public final class DesktopUpdatesDtos {
    private DesktopUpdatesDtos() {}

    public record KnownRelease(String skillId,String releaseId) {}
    public record Request(List<KnownRelease> items) {}
    public enum Status { update_available, up_to_date, unavailable, unknown_release }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String skillId,String currentReleaseId,Status status,DesktopDtos.ReleaseRef latestRelease) {}
    public record UpdateCheck(Instant checkedAt,List<Item> items) {}

    /** Inspect JSON types explicitly: Jackson's String coercion would accept numeric IDs. */
    public static Request parse(JsonNode body) {
        if(body==null || !body.isObject() || body.size()!=1 || !body.has("items"))throw invalid();
        JsonNode items=body.get("items");
        if(!items.isArray() || items.isEmpty() || items.size()>100)throw invalid();
        var result=new ArrayList<KnownRelease>(items.size());
        var seen=new HashSet<KnownRelease>();
        for(JsonNode item:items) {
            if(!item.isObject() || item.size()!=2 || !item.has("skillId") || !item.has("releaseId"))throw invalid();
            String skillId=id(item.get("skillId")),releaseId=id(item.get("releaseId"));
            // Store business IDs are positive bigint values; retain the exact wire identity.
            if(!skillId.matches("[1-9][0-9]{0,18}"))throw invalid();
            try { Long.parseLong(skillId); } catch(NumberFormatException overflow) { throw invalid(); }
            var pair=new KnownRelease(skillId,releaseId);
            if(!seen.add(pair))throw invalid();
            result.add(pair);
        }
        return new Request(List.copyOf(result));
    }
    private static String id(JsonNode node) {
        if(node==null || !node.isTextual())throw invalid();
        String value=node.textValue();
        if(value.isBlank() || value.length()>256 || !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl))throw invalid();
        return value;
    }
    static DesktopFailure invalid() {
        return new DesktopFailure(400,"INVALID_REQUEST","Expected 1 to 100 unique skill/release pairs with string IDs.");
    }
}

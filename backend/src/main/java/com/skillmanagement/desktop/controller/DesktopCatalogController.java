package com.skillmanagement.desktop.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.desktop.dto.DesktopDtos;
import com.skillmanagement.desktop.service.DesktopCatalogService;
import com.skillmanagement.user.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/desktop")
@Profile({"desktop-pilot","desktop-test"})
public class DesktopCatalogController {
    private final DesktopCatalogService catalog;
    public DesktopCatalogController(DesktopCatalogService catalog) { this.catalog=catalog; }
    @GetMapping("/me") public ApiResponse<DesktopDtos.UserView> me(@AuthenticationPrincipal User user) { return ApiResponse.ok(catalog.me(user)); }
    @GetMapping("/taxonomy") public ApiResponse<DesktopDtos.Taxonomy> taxonomy() { return ApiResponse.ok(catalog.taxonomy()); }
    @GetMapping("/skills") public ApiResponse<DesktopDtos.Page<DesktopDtos.SkillSummary>> search(@AuthenticationPrincipal User user,
        @RequestParam(name="q",defaultValue="") String q,@RequestParam(name="mode",defaultValue="all_time") String mode,
        @RequestParam(name="sourceId",required=false) String sourceId,@RequestParam(name="page",defaultValue="1") int page,@RequestParam(name="pageSize",defaultValue="20") int pageSize) {
        return ApiResponse.ok(catalog.search(user,q,mode,sourceId,page,pageSize));
    }
    @GetMapping("/skills/{skillId}") public ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal User user,@PathVariable("skillId") long skillId) { return ApiResponse.ok(catalog.detail(skillId,user)); }
}

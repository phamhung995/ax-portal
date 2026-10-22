package com.skillmanagement.skill.controller;

import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.skill.dto.SearchUserResult;
import com.skillmanagement.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User search for the Skill Management Creator field (Skill SRS §7.4).
 * Any authenticated user; ACTIVE users only.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class SkillUserSearchController {

    private final UserService userService;

    @GetMapping("/search-for-skill")
    public ResponseEntity<ApiResponse<SearchUserResult>> searchForSkill(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(ApiResponse.ok(userService.searchForSkill(keyword, page, pageSize)));
    }
}

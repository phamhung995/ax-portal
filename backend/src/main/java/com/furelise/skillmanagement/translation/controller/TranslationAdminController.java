package com.furelise.skillmanagement.translation.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.translation.dto.CreateTranslationRequest;
import com.furelise.skillmanagement.translation.dto.TranslationKeyItem;
import com.furelise.skillmanagement.translation.dto.UpdateTranslationRequest;
import com.furelise.skillmanagement.translation.service.TranslationService;
import com.furelise.skillmanagement.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Translation management endpoints (admin only).
 */
@RestController
@RequestMapping("/api/v1/admin/translations")
@RequiredArgsConstructor
@Secured("ROLE_ADMIN_USER")
public class TranslationAdminController {

    private final TranslationService translationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TranslationKeyItem>>> list(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.ok(translationService.list(search)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TranslationKeyItem>> create(
            @RequestBody CreateTranslationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(translationService.create(request, currentUserId())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TranslationKeyItem>> update(
            @PathVariable Long id, @RequestBody UpdateTranslationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(translationService.update(id, request, currentUserId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        translationService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }
}

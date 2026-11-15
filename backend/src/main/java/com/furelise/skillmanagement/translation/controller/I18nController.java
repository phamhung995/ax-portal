package com.furelise.skillmanagement.translation.controller;

import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.translation.dto.MessagesResponse;
import com.furelise.skillmanagement.translation.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public i18n endpoint: all UI messages for one language (any authenticated user).
 */
@RestController
@RequestMapping("/api/v1/i18n")
@RequiredArgsConstructor
public class I18nController {

    private final TranslationService translationService;

    @GetMapping("/messages")
    public ResponseEntity<ApiResponse<MessagesResponse>> messages(@RequestParam String lang) {
        return ResponseEntity.ok(ApiResponse.ok(
                new MessagesResponse(lang, translationService.messages(lang))));
    }
}

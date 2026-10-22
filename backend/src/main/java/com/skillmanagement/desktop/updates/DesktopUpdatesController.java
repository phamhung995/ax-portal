package com.skillmanagement.desktop.updates;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillmanagement.common.dto.ApiResponse;
import com.skillmanagement.desktop.service.DesktopFailure;
import com.skillmanagement.user.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/desktop/updates")
@Profile({"desktop-pilot","desktop-test"})
public class DesktopUpdatesController {
    private final DesktopUpdatesService updates;
    public DesktopUpdatesController(DesktopUpdatesService updates) { this.updates=updates; }

    @PostMapping("/check")
    public ApiResponse<DesktopUpdatesDtos.UpdateCheck> check(@RequestBody JsonNode body,@AuthenticationPrincipal User viewer) {
        return ApiResponse.ok(updates.check(DesktopUpdatesDtos.parse(body),viewer));
    }

    // Existing DesktopErrors advice only targets desktop.controller; keep this
    // new route's envelope local without changing that advice or security chain.
    @ExceptionHandler(DesktopFailure.class)
    public ResponseEntity<ApiResponse<Void>> failure(DesktopFailure failure) {
        return ResponseEntity.status(failure.status()).body(ApiResponse.error(failure.code(),failure.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> malformed() { return failure(DesktopUpdatesDtos.invalid()); }
}

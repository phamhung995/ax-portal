package com.furelise.skillmanagement.reputation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.furelise.skillmanagement.common.dto.ApiResponse;
import com.furelise.skillmanagement.reputation.dto.InstallEventRequest;
import com.furelise.skillmanagement.reputation.dto.InstallEventResponse;
import com.furelise.skillmanagement.reputation.service.CompanionAuthService;
import com.furelise.skillmanagement.reputation.service.InstallEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Companion install-events intake (GĐ6, BA FR-6). Machine-to-machine only:
 * the companion app reports 1-click install outcomes here after each
 * install attempt. This controller is NOT part of the read-only
 * {@link ReputationController} — it writes signal data.
 *
 * <p>Auth is the companion's own (BA Q5, chốt 18/11), NOT the user JWT:
 * the endpoint is permitAll in SecurityConfig and the three checks
 * (token, HMAC signature, rate-limit) run in
 * {@link CompanionAuthService} before the body is parsed. The raw body is
 * read as bytes so the HMAC is verified over exactly what the companion
 * sent — parsing happens only after the signature checks out.</p>
 *
 * <p>Headers: {@code X-Companion-Token} (or {@code Authorization: Bearer
 * <token>}), {@code X-Companion-Signature} = lowercase hex HMAC-SHA256 of
 * the raw body, keyed with the token.</p>
 */
@RestController
@RequestMapping("/api/v1/reputation")
@RequiredArgsConstructor
public class InstallEventController {

    private final CompanionAuthService companionAuthService;
    private final InstallEventService installEventService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/install-events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<InstallEventResponse>> report(
            @RequestHeader(value = "X-Companion-Token", required = false) String tokenHeader,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Companion-Signature", required = false) String signature,
            @RequestBody byte[] rawBody) {
        companionAuthService.authenticate(tokenHeader, authorizationHeader, signature, rawBody);
        InstallEventRequest request;
        try {
            request = objectMapper.readValue(rawBody, InstallEventRequest.class);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Malformed JSON body.");
        }
        return ResponseEntity.ok(ApiResponse.ok(installEventService.record(request)));
    }
}

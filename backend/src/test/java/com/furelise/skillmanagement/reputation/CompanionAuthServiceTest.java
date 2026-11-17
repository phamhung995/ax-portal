package com.furelise.skillmanagement.reputation;

import com.furelise.skillmanagement.exception.CompanionAuthException;
import com.furelise.skillmanagement.reputation.service.CompanionAuthService;
import com.furelise.skillmanagement.reputation.service.CompanionRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Companion auth (GĐ6, BA Q5): token (X-Companion-Token or Authorization:
 * Bearer), HMAC-SHA256 signature of the raw body keyed with the token,
 * rate-limit. Fail closed (503) when no token is configured.
 */
class CompanionAuthServiceTest {

    private static final String TOKEN = "dev-token-for-tests-only";
    private static final byte[] BODY = "{\"targetType\":\"SKILL\",\"targetId\":1,\"success\":true}".getBytes(StandardCharsets.UTF_8);

    private CompanionAuthService authService() {
        return new CompanionAuthService(
                new CompanionRateLimiter(1000, Duration.ofMinutes(1)), TOKEN);
    }

    private static String sign(byte[] body, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    private static void assertStatus(CompanionAuthException ex, HttpStatus status) {
        assertEquals(status, ex.getStatus());
    }

    @Test
    void validTokenHeaderAndSignaturePasses() throws Exception {
        authService().authenticate(TOKEN, null, sign(BODY, TOKEN), BODY);
    }

    @Test
    void bearerTokenIsAccepted() throws Exception {
        authService().authenticate(null, "Bearer " + TOKEN, sign(BODY, TOKEN), BODY);
    }

    @Test
    void missingTokenIs401() {
        CompanionAuthException ex = assertThrows(CompanionAuthException.class,
                () -> authService().authenticate(null, null, "deadbeef", BODY));
        assertStatus(ex, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongTokenIs401() {
        CompanionAuthException ex = assertThrows(CompanionAuthException.class,
                () -> authService().authenticate("wrong-token", null, "deadbeef", BODY));
        assertStatus(ex, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void missingSignatureIs403() {
        CompanionAuthException ex = assertThrows(CompanionAuthException.class,
                () -> authService().authenticate(TOKEN, null, null, BODY));
        assertStatus(ex, HttpStatus.FORBIDDEN);
    }

    @Test
    void wrongSignatureIs403() throws Exception {
        String otherBodySig = sign("{\"tampered\":true}".getBytes(StandardCharsets.UTF_8), TOKEN);
        CompanionAuthException ex = assertThrows(CompanionAuthException.class,
                () -> authService().authenticate(TOKEN, null, otherBodySig, BODY));
        assertStatus(ex, HttpStatus.FORBIDDEN);
    }

    @Test
    void signatureIsCaseInsensitiveHex() throws Exception {
        authService().authenticate(TOKEN, null, sign(BODY, TOKEN).toUpperCase(), BODY);
    }

    @Test
    void emptyConfiguredTokenRefusesEverythingWith503() {
        CompanionAuthService unconfigured = new CompanionAuthService(
                new CompanionRateLimiter(1000, Duration.ofMinutes(1)), "");
        CompanionAuthException ex = assertThrows(CompanionAuthException.class,
                () -> unconfigured.authenticate(TOKEN, null, "deadbeef", BODY));
        assertStatus(ex, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rateLimitExceededIs429() throws Exception {
        CompanionAuthService limited = new CompanionAuthService(
                new CompanionRateLimiter(1, Duration.ofMinutes(1)), TOKEN);
        limited.authenticate(TOKEN, null, sign(BODY, TOKEN), BODY);
        CompanionAuthException ex = assertThrows(CompanionAuthException.class,
                () -> limited.authenticate(TOKEN, null, sign(BODY, TOKEN), BODY));
        assertStatus(ex, HttpStatus.TOO_MANY_REQUESTS);
    }
}

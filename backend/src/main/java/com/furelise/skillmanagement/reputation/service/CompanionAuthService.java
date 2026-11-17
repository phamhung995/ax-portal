package com.furelise.skillmanagement.reputation.service;

import com.furelise.skillmanagement.exception.CompanionAuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Companion-app authentication (GĐ6, BA Q5 — chốt 18/11): three layers,
 * checked in this order.
 *
 * <ol>
 *   <li><b>Token.</b> {@code X-Companion-Token} header, or
 *       {@code Authorization: Bearer <token>} — compared (constant-time)
 *       against {@code ax-portal.companion.token} (env COMPANION_TOKEN).
 *       Missing/invalid → 401. When the property is empty the endpoint
 *       refuses ALL calls with 503 (fail closed): an unconfigured server
 *       must never accept unauthenticated install reports.</li>
 *   <li><b>Signature.</b> {@code X-Companion-Signature} must be the lowercase
 *       hex HMAC-SHA256 of the raw request body, keyed with the token.
 *       Missing/wrong → 403. Using the token itself as the HMAC key is a
 *       deliberate simplification: one secret to share instead of two. A
 *       separate signing secret ({@code ax-portal.companion.signing-secret})
 *       can be split out later without changing the wire format.</li>
 *   <li><b>Rate-limit.</b> Token bucket (default 120/min, see
 *       {@link CompanionRateLimiter}); exceeded → 429. Only fully
 *       authenticated requests consume quota, so bad signatures cannot
 *       burn the companion's budget.</li>
 * </ol>
 *
 * The real token is set via the COMPANION_TOKEN env var on the server and
 * shared with the companion team out of band. NEVER commit a production
 * token; the value in application.yml is intentionally empty by default.
 */
@Service
public class CompanionAuthService {

    private final CompanionRateLimiter rateLimiter;
    private final String configuredToken;

    public CompanionAuthService(CompanionRateLimiter rateLimiter,
                                @Value("${ax-portal.companion.token:}") String configuredToken) {
        this.rateLimiter = rateLimiter;
        this.configuredToken = configuredToken;
    }

    /**
     * Authenticate one install-events call. Throws {@link CompanionAuthException}
     * (mapped to 401/403/429/503 by GlobalExceptionHandler) on any failure;
     * returns normally when the call is authenticated.
     *
     * @param tokenHeader         value of X-Companion-Token, may be null
     * @param authorizationHeader value of Authorization, may be null
     * @param signature           value of X-Companion-Signature, may be null
     * @param rawBody             the request body exactly as received
     */
    public void authenticate(String tokenHeader, String authorizationHeader,
                             String signature, byte[] rawBody) {
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new CompanionAuthException(HttpStatus.SERVICE_UNAVAILABLE,
                    "COMPANION_NOT_CONFIGURED",
                    "Companion token is not configured on the server.");
        }
        String presented = tokenHeader != null && !tokenHeader.isBlank()
                ? tokenHeader.trim()
                : bearerToken(authorizationHeader);
        if (presented == null || !constantTimeEquals(presented, configuredToken)) {
            throw new CompanionAuthException(HttpStatus.UNAUTHORIZED,
                    "COMPANION_UNAUTHORIZED",
                    "Missing or invalid companion token.");
        }
        String expected = hmacHex(rawBody, configuredToken);
        if (signature == null
                || !constantTimeEquals(signature.toLowerCase(Locale.ROOT), expected)) {
            throw new CompanionAuthException(HttpStatus.FORBIDDEN,
                    "COMPANION_BAD_SIGNATURE",
                    "Invalid companion signature.");
        }
        if (!rateLimiter.tryAcquire()) {
            throw new CompanionAuthException(HttpStatus.TOO_MANY_REQUESTS,
                    "COMPANION_RATE_LIMITED",
                    "Companion rate limit exceeded.");
        }
    }

    private static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** Constant-time comparison so token/signature guessing leaks no timing. */
    static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /** Lowercase hex HMAC-SHA256 of the raw body, keyed with the token. */
    static String hmacHex(byte[] rawBody, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}

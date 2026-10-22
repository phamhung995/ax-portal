package com.skillmanagement.auth;

import com.skillmanagement.model.Role;
import com.skillmanagement.user.model.User;
import com.skillmanagement.auth.service.JwtService;
import com.skillmanagement.auth.service.impl.JwtServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    // 256-bit base64 secret key
    private static final String SECRET_KEY = "ZGV2LW9ubHktc2VjcmV0LWtleS1tdXN0LWNoYW5nZS1pbi1wcm9kdWN0aW9u";
    private static final long EXPIRATION_MS = 900000; // 15 minutes

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(SECRET_KEY, EXPIRATION_MS);
        testUser = User.builder()
                .name("JWT Tester")
                .email("jwt@example.com")
                .password("encoded_pass")
                .role(Role.builder().name("USER").build())
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("generateToken should include valid UUID jti claim and extractJti should return it")
    void testJtiClaimPresence() {
        String token = jwtService.generateToken(testUser);
        assertNotNull(token);

        String jti = jwtService.extractJti(token);
        assertNotNull(jti);
        assertDoesNotThrow(() -> java.util.UUID.fromString(jti));
    }

    @Test
    @DisplayName("extractExpiration should return token expiration in future")
    void testExtractExpiration() {
        String token = jwtService.generateToken(testUser);
        Date expiration = jwtService.extractExpiration(token);

        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }
}

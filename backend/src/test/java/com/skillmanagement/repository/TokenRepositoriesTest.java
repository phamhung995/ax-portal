package com.skillmanagement.repository;

import com.skillmanagement.auth.model.BlacklistedToken;
import com.skillmanagement.auth.repository.BlacklistedTokenRepository;
import com.skillmanagement.auth.repository.RefreshTokenRepository;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.auth.model.RefreshToken;
import com.skillmanagement.model.Role;
import com.skillmanagement.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class TokenRepositoriesTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.save(Role.builder().name("USER").description("User").build());
        testUser = userRepository.save(User.builder()
                .name("Token User")
                .email("tokenuser@example.com")
                .password("password123")
                .role(role)
                .enabled(true)
                .build());
    }

    @Test
    @DisplayName("RefreshTokenRepository: save, findByToken, deleteByUserId")
    void testRefreshTokenCrud() {
        String tokenStr = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .user(testUser)
                .expiryDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        refreshTokenRepository.save(refreshToken);

        Optional<RefreshToken> found = refreshTokenRepository.findByToken(tokenStr);
        assertTrue(found.isPresent());
        assertEquals(testUser.getId(), found.get().getUser().getId());

        refreshTokenRepository.deleteByUserId(testUser.getId());
        assertTrue(refreshTokenRepository.findByToken(tokenStr).isEmpty());
    }

    @Test
    @DisplayName("BlacklistedTokenRepository: save, existsByJti, deleteByExpiryDateBefore")
    void testBlacklistedTokenCrud() {
        String jti = UUID.randomUUID().toString();
        BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                .jti(jti)
                .expiryDate(Instant.now().plus(15, ChronoUnit.MINUTES))
                .blacklistedAt(Instant.now())
                .build();

        blacklistedTokenRepository.save(blacklistedToken);

        assertTrue(blacklistedTokenRepository.existsByJti(jti));
        assertFalse(blacklistedTokenRepository.existsByJti("non-existent-jti"));
    }
}

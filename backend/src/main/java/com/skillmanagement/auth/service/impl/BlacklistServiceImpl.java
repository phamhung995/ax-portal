package com.skillmanagement.auth.service.impl;

import com.skillmanagement.auth.model.BlacklistedToken;
import com.skillmanagement.auth.repository.BlacklistedTokenRepository;
import com.skillmanagement.auth.service.BlacklistService;
import com.skillmanagement.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlacklistServiceImpl implements BlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final JwtService jwtService;

    @Override
    @Transactional
    public void blacklistToken(String jwtToken) {
        try {
            String jti = jwtService.extractJti(jwtToken);
            Date expiration = jwtService.extractExpiration(jwtToken);

            if (jti != null && expiration != null && expiration.after(new Date())) {
                BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                        .jti(jti)
                        .expiryDate(expiration.toInstant())
                        .blacklistedAt(Instant.now())
                        .build();
                blacklistedTokenRepository.save(blacklistedToken);
                log.info("Token with JTI {} has been blacklisted until {}", jti, expiration);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTokenBlacklisted(String jwtToken) {
        try {
            String jti = jwtService.extractJti(jwtToken);
            if (jti == null) {
                return false;
            }
            return blacklistedTokenRepository.existsByJti(jti);
        } catch (Exception e) {
            return false;
        }
    }
}

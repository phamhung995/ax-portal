package com.skillmanagement.auth.service;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.function.Function;

/**
 * Service interface for JWT token operations.
 */
public interface JwtService {

    String extractUsername(String jwtToken);

    <T> T extractClaim(String jwtToken, Function<Claims, T> claimsResolver);

    String generateToken(Map<String, Object> extraClaims, UserDetails userDetails);

    String generateToken(UserDetails userDetails);

    boolean isTokenValid(String jwtToken, UserDetails userDetails);

    String extractJti(String jwtToken);

    java.util.Date extractExpiration(String jwtToken);
}


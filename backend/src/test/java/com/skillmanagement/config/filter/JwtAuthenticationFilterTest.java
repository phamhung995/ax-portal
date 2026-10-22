package com.skillmanagement.config.filter;

import com.skillmanagement.auth.service.BlacklistService;
import com.skillmanagement.auth.service.JwtService;
import com.skillmanagement.config.filter.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private BlacklistService blacklistService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, userDetailsService, blacklistService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should block authentication if token is blacklisted")
    void testBlacklistedTokenBlocksAuth() throws Exception {
        String token = "blacklisted.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(blacklistService.isTokenBlacklisted(token)).thenReturn(true);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Should authenticate user if token is valid and NOT blacklisted")
    void testValidTokenSetsAuth() throws Exception {
        String token = "valid.jwt.token";
        String email = "valid@example.com";
        UserDetails userDetails = new User(email, "pass", Collections.emptyList());

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(blacklistService.isTokenBlacklisted(token)).thenReturn(false);
        when(jwtService.extractUsername(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}

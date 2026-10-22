package com.skillmanagement.config;

import com.skillmanagement.config.filter.JwtAuthenticationFilter;
import com.skillmanagement.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 * Uses method-level security (@Secured) for role-based access control.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(securedEnabled = true, prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    @Value("${app.upload.avatar-url-prefix:/uploads/avatars}")
    private String avatarUrlPrefix;

    @Value("${app.upload.skill-request-url-prefix:/uploads/skill-requests}")
    private String skillRequestUrlPrefix;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String avatarUrl = stripTrailingSlash(avatarUrlPrefix);
        String skillRequestUrl = stripTrailingSlash(skillRequestUrlPrefix);
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/health/**",
                                "/actuator/**",
                                avatarUrl + "/**",
                                skillRequestUrl + "/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeErrorEnvelope(response, 401, "UNAUTHORIZED", "Your session has expired. Please log in again."))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeErrorEnvelope(response, 403, "FORBIDDEN", "You do not have permission to access this resource."))
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Writes the standard error envelope so the frontend error interceptor
     * (which expects { success, errorCode, message }) can map it correctly.
     */
    private void writeErrorEnvelope(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        new ObjectMapper().writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}

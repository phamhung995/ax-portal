package com.furelise.skillmanagement.translation;

import com.furelise.skillmanagement.config.filter.JwtAuthenticationFilter;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.repository.RoleRepository;
import com.furelise.skillmanagement.translation.dto.CreateTranslationRequest;
import com.furelise.skillmanagement.translation.dto.UpdateTranslationRequest;
import com.furelise.skillmanagement.translation.service.TranslationService;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the public i18n endpoint and the admin translation
 * endpoints: guest access (200), role (403), lang validation (400), and
 * cache invalidation after admin updates.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:i18nwebdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS TEXT")
class I18nControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TranslationService translationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    @TestConfiguration
    static class TestSecurity {
        /** Replace the JWT filter with a no-op so tests set the SecurityContext directly. */
        @Bean
        @org.springframework.context.annotation.Primary
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(null, null, null) {
                @Override
                protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                jakarta.servlet.FilterChain filterChain)
                        throws jakarta.servlet.ServletException, java.io.IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }

    private User admin;
    private User normal;

    @BeforeEach
    void seed() {
        SecurityContextHolder.clearContext();
        Role normalRole = role("NORMAL_USER");
        Role adminRole = role("ADMIN_USER");
        String suffix = Long.toHexString(System.nanoTime());
        normal = user("Normal", "i18n-normal-" + suffix + "@test.local", normalRole);
        admin = user("Admin", "i18n-admin-" + suffix + "@test.local", adminRole);
    }

    private Role role(String name) {
        return roleRepository.findAll().stream()
                .filter(r -> name.equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(Role.builder().name(name).description("t").build()));
    }

    private User user(String name, String email, Role role) {
        return userRepository.save(User.builder().name(name).email(email)
                .password("x").role(role).build());
    }

    private void authenticateAs(User u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test
    void messages_endpoint_returns_envelope_with_lang_and_messages() throws Exception {
        authenticateAs(normal);
        Long id = translationService.create(
                new CreateTranslationRequest("app.webtest", null, Map.of("vi", "WebVi", "en", "WebEn")), admin.getId()).id();

        mockMvc.perform(get("/api/v1/i18n/messages").param("lang", "vi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lang").value("vi"))
                .andExpect(jsonPath("$.data.messages['app.webtest']").value("WebVi"));
    }

    @Test
    void messages_endpoint_rejects_unknown_lang() throws Exception {
        authenticateAs(normal);
        mockMvc.perform(get("/api/v1/i18n/messages").param("lang", "fr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_LANGUAGE"));
    }

    @Test
    void messages_endpoint_is_public_for_guests() throws Exception {
        // The messages endpoint is public (guests can load translations
        // before logging in) — no auth required.
        mockMvc.perform(get("/api/v1/i18n/messages").param("lang", "vi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lang").value("vi"));
    }

    @Test
    void admin_endpoints_reject_non_admin() throws Exception {
        authenticateAs(normal);
        mockMvc.perform(get("/api/v1/admin/translations"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cache_invalidated_after_update() throws Exception {
        authenticateAs(admin);
        Long id = translationService.create(
                new CreateTranslationRequest("app.cache", null, Map.of("en", "Before")), admin.getId()).id();

        // prime the cache
        Map<String, String> first = translationService.messages("en");
        assertThat(first.get("app.cache")).isEqualTo("Before");

        translationService.update(id, new UpdateTranslationRequest(null, Map.of("en", "After")), admin.getId());

        Map<String, String> second = translationService.messages("en");
        assertThat(second.get("app.cache")).isEqualTo("After");
    }
}

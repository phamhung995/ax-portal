package com.furelise.skillmanagement.translation;

import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.repository.RoleRepository;
import com.furelise.skillmanagement.translation.dto.CreateTranslationRequest;
import com.furelise.skillmanagement.translation.dto.TranslationKeyItem;
import com.furelise.skillmanagement.translation.dto.UpdateTranslationRequest;
import com.furelise.skillmanagement.translation.model.Translation;
import com.furelise.skillmanagement.translation.model.TranslationKey;
import com.furelise.skillmanagement.translation.repository.TranslationKeyRepository;
import com.furelise.skillmanagement.translation.repository.TranslationRepository;
import com.furelise.skillmanagement.translation.service.impl.TranslationServiceImpl;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * H2 tests for the translation module (V56 translation_keys / translations).
 * Covers admin CRUD, key validation, per-language merge/clear semantics,
 * search, and the public messages() fallback behavior.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:translationdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS TEXT")
@Import(TranslationServiceImpl.class)
class TranslationServiceTest {

    @Autowired private TranslationKeyRepository keyRepository;
    @Autowired private TranslationRepository translationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TranslationServiceImpl service;

    @MockitoBean
    private AuditService auditService;

    private Long adminId;

    @BeforeEach
    void seed() {
        Role role = roleRepository.findAll().stream()
                .filter(r -> "NORMAL_USER".equals(r.getName()))
                .findFirst()
                .orElseGet(() -> roleRepository.save(Role.builder().name("NORMAL_USER").description("t").build()));
        User admin = User.builder().name("System Admin").email("admin@test.local")
                .password("x").role(role).build();
        adminId = userRepository.save(admin).getId();
    }

    private CreateTranslationRequest createReq(String key, Map<String, String> values) {
        return new CreateTranslationRequest(key, "desc: " + key, values);
    }

    @Test
    void create_valid_key_persists_all_three_languages() {
        TranslationKeyItem item = service.create(
                createReq("app.test.key", Map.of("en", "En", "vi", "Vi", "ko", "Ko")), adminId);

        assertThat(item.key()).isEqualTo("app.test.key");
        assertThat(item.values()).containsExactlyInAnyOrderEntriesOf(
                Map.of("en", "En", "vi", "Vi", "ko", "Ko"));
        assertThat(translationRepository.count()).isEqualTo(3);
        verify(auditService).log(ArgumentMatchers.eq("CREATE"),
                ArgumentMatchers.eq("TRANSLATION"), ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    @Test
    void create_duplicate_key_throws_translation_key_exists() {
        service.create(createReq("app.dup", Map.of("en", "En")), adminId);
        assertThatThrownBy(() -> service.create(createReq("app.dup", Map.of("en", "En2")), adminId))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessErrorCode.TRANSLATION_KEY_EXISTS);
    }

    @Test
    void create_invalid_key_format_throws_translation_key_invalid() {
        for (String bad : List.of("123abc", "has space", "a.".repeat(61))) {
            assertThatThrownBy(() -> service.create(createReq(bad, Map.of("en", "En")), adminId))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(BusinessErrorCode.TRANSLATION_KEY_INVALID);
        }
    }

    @Test
    void update_partial_values_merges_and_clears_empty() {
        TranslationKeyItem created = service.create(
                createReq("app.merge", Map.of("en", "En", "vi", "Vi", "ko", "Ko")), adminId);

        TranslationKeyItem updated = service.update(created.id(),
                new UpdateTranslationRequest("new desc", Map.of("vi", "")), adminId);

        assertThat(updated.description()).isEqualTo("new desc");
        assertThat(updated.values()).containsExactlyInAnyOrderEntriesOf(
                Map.of("en", "En", "ko", "Ko"));
        verify(auditService).log(ArgumentMatchers.eq("UPDATE"),
                ArgumentMatchers.eq("TRANSLATION"), ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    @Test
    void update_unknown_key_throws_translation_key_not_found() {
        assertThatThrownBy(() -> service.update(999L, new UpdateTranslationRequest(null, Map.of("en", "X")), adminId))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessErrorCode.TRANSLATION_KEY_NOT_FOUND);
    }

    @Test
    void delete_removes_key_and_cascades_translations() {
        TranslationKeyItem created = service.create(
                createReq("app.del", Map.of("en", "En", "vi", "Vi")), adminId);
        assertThat(translationRepository.count()).isEqualTo(2);

        service.delete(created.id());

        assertThat(keyRepository.findById(created.id())).isEmpty();
        assertThat(translationRepository.count()).isZero();
        verify(auditService).log(ArgumentMatchers.eq("DELETE"),
                ArgumentMatchers.eq("TRANSLATION"), ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    @Test
    void list_searches_by_key_and_description() {
        service.create(createReq("app.nav.home", Map.of("en", "Home")), adminId);
        service.create(createReq("app.other", Map.of("en", "Other")), adminId);
        keyRepository.findById(keyRepository.findByKey("app.other").orElseThrow().getId())
                .ifPresent(k -> {
                    k.setDescription("sidebar entry");
                    keyRepository.save(k);
                });

        assertThat(service.list("nav").stream().map(TranslationKeyItem::key))
                .containsExactly("app.nav.home");
        assertThat(service.list("sidebar").stream().map(TranslationKeyItem::key))
                .containsExactly("app.other");
        assertThat(service.list(null)).hasSize(2);
    }

    @Test
    void messages_returns_lang_values_with_en_fallback() {
        service.create(createReq("app.only.en", Map.of("en", "OnlyEn")), adminId);
        service.create(createReq("app.tri", Map.of("en", "E", "vi", "V", "ko", "K")), adminId);

        Map<String, String> vi = service.messages("vi");
        assertThat(vi).containsEntry("app.tri", "V");
        assertThat(vi).containsEntry("app.only.en", "OnlyEn"); // en fallback

        Map<String, String> ko = service.messages("ko");
        assertThat(ko).containsEntry("app.tri", "K");
        assertThat(ko).containsEntry("app.only.en", "OnlyEn");
    }

    @Test
    void messages_invalid_lang_throws_invalid_language() {
        assertThatThrownBy(() -> service.messages("fr"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessErrorCode.INVALID_LANGUAGE);
    }
}

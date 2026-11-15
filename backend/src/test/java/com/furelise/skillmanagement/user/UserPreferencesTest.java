package com.furelise.skillmanagement.user;

import com.furelise.skillmanagement.auth.dto.MeResponse;
import com.furelise.skillmanagement.user.service.FileStorageService;
import com.furelise.skillmanagement.common.RateLimiter;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.user.dto.PreferencesRequest;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.model.UserSettings;
import com.furelise.skillmanagement.user.repository.UserHistoryRepository;
import com.furelise.skillmanagement.user.repository.UserRepository;
import com.furelise.skillmanagement.user.repository.UserSettingsRepository;
import com.furelise.skillmanagement.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for user preference validation (language/timezone/date format).
 */
@ExtendWith(MockitoExtension.class)
class UserPreferencesTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private UserHistoryRepository userHistoryRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RateLimiter rateLimiter;

    private UserServiceImpl service;
    private User user;
    private UserSettings settings;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, userSettingsRepository, userHistoryRepository,
                organizationRepository, fileStorageService, passwordEncoder, rateLimiter);
        user = User.builder().id(1L).name("U").email("u@test.local").password("x").build();
        settings = UserSettings.builder().userId(1L).language("en")
                .timezone("Asia/Ho_Chi_Minh").dateFormat("DD/MM/YYYY").build();
    }

    private void stubUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(settings));
        when(organizationRepository.findByKnoxIdIgnoreCase(any())).thenReturn(List.of());
    }

    @Test
    void updatePreferences_language_ko_is_accepted() {
        stubUser();
        when(userSettingsRepository.save(any(UserSettings.class))).thenReturn(settings);

        MeResponse me = service.updatePreferences(1L,
                new PreferencesRequest("ko", "Asia/Ho_Chi_Minh", "DD/MM/YYYY"));

        assertThat(me.settings().language()).isEqualTo("ko");
        assertThat(settings.getLanguage()).isEqualTo("ko");
    }

    @Test
    void updatePreferences_language_fr_is_rejected() {
        assertThatThrownBy(() -> service.updatePreferences(1L,
                new PreferencesRequest("fr", "Asia/Ho_Chi_Minh", "DD/MM/YYYY")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessErrorCode.INVALID_LANGUAGE);
    }
}

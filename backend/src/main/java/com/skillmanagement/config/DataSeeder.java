package com.skillmanagement.config;

import com.skillmanagement.notification.model.Notification;
import com.skillmanagement.model.Role;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.model.UserSettings;
import com.skillmanagement.notification.repository.NotificationRepository;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Initializes default roles and the default admin user on startup if not present.
 * The admin password is generated at runtime and logged to the console once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Role normalRole = roleRepository.findByName("NORMAL_USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("NORMAL_USER").description("Người dùng tiêu chuẩn").build()));
        Role adminRole = roleRepository.findByName("ADMIN_USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN_USER").description("Quản trị viên hệ thống").build()));

        if (userRepository.findByEmailIgnoreCase("admin@skillmgmt.local").isEmpty()) {
            String adminPassword = "Admin@12345";
            User admin = User.builder()
                    .name("System Admin")
                    .email("admin@skillmgmt.local")
                    .password(passwordEncoder.encode(adminPassword))
                    .role(adminRole)
                    .status("ACTIVE")
                    .createdAppId("SYSTEM")
                    .build();
            userRepository.save(admin);

            userSettingsRepository.save(UserSettings.builder()
                    .userId(admin.getId())
                    .emailNotify(true).inappNotify(true)
                    .build());

            // Demo notifications for the GNB bell
            notificationRepository.save(Notification.builder()
                    .userId(admin.getId())
                    .title("Skill 'Java Spring Boot' has a new version")
                    .skillId("SK0001")
                    .createdAt(Instant.now().minus(2, java.time.temporal.ChronoUnit.HOURS))
                    .build());
            notificationRepository.save(Notification.builder()
                    .userId(admin.getId())
                    .title("Skill 'Python Data Analysis' was rated 5 stars")
                    .skillId("SK0002")
                    .createdAt(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                    .build());

            log.info("====================================================================");
            log.info("[SEED] Default admin created: admin@skillmgmt.local / {}", adminPassword);
            log.info("[SEED] (Dev only — change this credential before production)");
            log.info("====================================================================");
        }
    }
}

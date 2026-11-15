package com.furelise.skillmanagement.config;

import com.furelise.skillmanagement.ainews.model.AiNewsSource;
import com.furelise.skillmanagement.ainews.repository.AiNewsSourceRepository;
import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.org.model.OrganizationMaster;
import com.furelise.skillmanagement.org.repository.OrganizationRepository;
import com.furelise.skillmanagement.repository.RoleRepository;
import com.furelise.skillmanagement.user.model.User;
import com.furelise.skillmanagement.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Seeds an {@code organization_master} row for the default admin
 * ({@code admin@skillmgmt.local} → knox_id {@code admin}) so the login
 * org check (SRS §8 step 5: at least one org row with employed='Y') passes
 * under the {@code local-h2} profile, where Flyway is off and no seed
 * migration runs.
 *
 * <p>Also seeds the GDC {@code ai_news_source} row (the V24 seed does not
 * run under this profile) so a manual batch run
 * ({@code POST /api/v1/admin/ai-news/run}) has an ACTIVE source to read.</p>
 *
 * <p>The default admin USER is also seeded here (the DataSeeder
 * auto-creation was removed in the org restructure) — only under this
 * profile, so dev/prod are unaffected. The AI News local-h2 integration
 * tests log in as this admin.</p>
 */
@Slf4j
@Component
@Profile("local-h2")
@RequiredArgsConstructor
public class LocalH2TestSeeder implements CommandLineRunner {

    private final OrganizationRepository organizationRepository;
    private final AiNewsSourceRepository aiNewsSourceRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByEmailIgnoreCase("admin@skillmgmt.local").isEmpty()) {
            Role adminRole = roleRepository.findByName("ADMIN_USER").orElseGet(() ->
                    roleRepository.save(Role.builder().name("ADMIN_USER").description("Quản trị viên hệ thống").build()));
            User admin = User.builder()
                    .name("System Admin")
                    .email("admin@skillmgmt.local")
                    .knoxId("admin")
                    .password(passwordEncoder.encode("Admin@12345"))
                    .role(adminRole)
                    .status("ACTIVE")
                    .createdAppId("SYSTEM")
                    .build();
            userRepository.save(admin);
            log.info("[SEED] local-h2: default admin user created (admin@skillmgmt.local)");
        }
        if (organizationRepository.findByKnoxIdIgnoreCase("admin").isEmpty()) {
            organizationRepository.save(OrganizationMaster.builder()
                    .knoxId("admin")
                    .userName("System Admin")
                    .employed("Y")
                    .regularPartner("Regular")
                    .company("Samsung")
                    .part("TestPart")
                    .groupName("TestGroup")
                    .employeeNumber("ADMIN0001")
                    .importedAt(Instant.now())
                    .build());
            log.info("[SEED] local-h2: org row for admin (knox_id=admin, employed=Y) created");
        }
        if (aiNewsSourceRepository.findByMailbox("gdcv.cag.ax").isEmpty()) {
            aiNewsSourceRepository.save(AiNewsSource.builder()
                    .mailbox("gdcv.cag.ax")
                    .secretRef("gdc-tech-support")
                    .displayName("GDC Technical Support")
                    .status(AiNewsSource.STATUS_ACTIVE)
                    .updatedAt(Instant.now())
                    .build());
            log.info("[SEED] local-h2: ai_news_source row for gdcv.cag.ax (ACTIVE) created");
        }
    }
}

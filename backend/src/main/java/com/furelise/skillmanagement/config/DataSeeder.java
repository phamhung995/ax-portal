package com.furelise.skillmanagement.config;

import com.furelise.skillmanagement.model.Role;
import com.furelise.skillmanagement.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes default roles on startup if not present.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        Role normalRole = roleRepository.findByName("NORMAL_USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("NORMAL_USER").description("Người dùng tiêu chuẩn").build()));
        Role adminRole = roleRepository.findByName("ADMIN_USER").orElseGet(() ->
                roleRepository.save(Role.builder().name("ADMIN_USER").description("Quản trị viên hệ thống").build()));
    }
}

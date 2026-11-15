package com.furelise.skillmanagement.repository;

import com.furelise.skillmanagement.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void shouldSaveAndFindRoleByName() {
        Role role = Role.builder()
                .name("ADMIN")
                .description("Quản trị viên")
                .build();
        roleRepository.save(role);

        var found = roleRepository.findByName("ADMIN");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("ADMIN");
        assertThat(found.get().getDescription()).isEqualTo("Quản trị viên");
    }
}

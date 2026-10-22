package com.skillmanagement.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmanagement.desktop.repository.DesktopReleaseRepository;
import com.skillmanagement.desktop.service.DesktopArtifactStorage;
import com.skillmanagement.model.Role;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"knox.mail.system-id=", "spring.datasource.url=jdbc:h2:mem:desktopreleases;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"})
@AutoConfigureMockMvc
@ActiveProfiles({"test","desktop-test"})
@Transactional
class DesktopReleaseTest {
    @TempDir static Path storage;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("desktop.pilot.package-root",()->storage.toString()); }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired SkillRepository skills;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DesktopReleaseRepository releases;
    @Autowired DesktopArtifactStorage artifacts;
    User creator,viewer,admin;
    Skill skill;
    @BeforeEach void setup() throws Exception {
        DesktopTestSchema.create(jdbc);
        var ordinary=roles.save(Role.builder().name("DESKTOP_RELEASE_VIEWER").build());
        var elevated=roles.findByName("ADMIN_USER").orElseGet(()->roles.save(Role.builder().name("ADMIN_USER").build()));
        creator=account("release-creator",ordinary); viewer=account("release-viewer",ordinary); admin=account("release-admin",elevated);
        skill=skills.saveAndFlush(Skill.builder().name("Immutable release skill").description("Synthetic test").createdBy(creator.getId()).approvalStatus("APPROVED").publicScope("PUBLIC").version(11).build());
    }
    User account(String name,Role role) { return users.saveAndFlush(User.builder().name(name).email(name+"@example.invalid").knoxId(name).password("unused").role(role).build()); }
    record Published(UUID id,UUID packageId,UUID key,byte[] bytes,String sha,long sequence) {}
    Published published(String format,long sequence) throws Exception {
        byte[] bytes=getClass().getResourceAsStream("/desktop/packages/sds-synthetic."+format).readAllBytes();
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        UUID id=UUID.randomUUID(),pkg=UUID.randomUUID(),key=UUID.randomUUID();
        Files.write(storage.resolve(key+".pkg"),bytes);
        jdbc.update("INSERT INTO desktop_package(id,format,file_name,size_bytes,sha256,storage_key) VALUES(?,?,?,?,?,?)",pkg,format,"pilot."+format,bytes.length,sha,key);
        Map<String,Object> metadata=new LinkedHashMap<>();
        metadata.put("skillId",skill.getId().toString()); metadata.put("id",id.toString()); metadata.put("sequence",sequence); metadata.put("version","1.0."+sequence); metadata.put("installName","sds-pilot-skill"); metadata.put("publishedAt","2026-10-17T00:00:00Z"); metadata.put("changelog","Synthetic release"); metadata.put("compatibleAgents",null); metadata.put("requiredMcps",sequence==1?List.of("jira-mcp"):List.of());
        jdbc.update("INSERT INTO desktop_release(id,skill_id,sequence,metadata_json,package_id,withdrawn) VALUES(?,?,?,?,?,false)",id,skill.getId(),sequence,mapper.writeValueAsString(metadata),pkg);
        return new Published(id,pkg,key,bytes,sha,sequence);
    }
    String route(Published release) { return "/api/v1/desktop/skills/"+skill.getId()+"/releases/"+release.id(); }

    @Test void zip_and_7z_are_exact_authenticated_bytes_with_frozen_metadata() throws Exception {
        for(var format:List.of("zip","7z")) {
            var release=published(format,format.equals("zip")?1:2);
            mvc.perform(get(route(release)).with(user(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.data.package.format").value(format)).andExpect(jsonPath("$.data.package.sha256").value(release.sha())).andExpect(jsonPath("$.data.package.sizeBytes").value(release.bytes().length)).andExpect(jsonPath("$.data.package.downloadPath").value(route(release)+"/package"));
            mvc.perform(get(route(release)+"/package")).andExpect(status().isUnauthorized());
            mvc.perform(get(route(release)+"/package").with(user(viewer))).andExpect(status().isOk()).andExpect(header().longValue("Content-Length",release.bytes().length)).andExpect(content().contentType(format.equals("zip")?"application/zip":"application/x-7z-compressed")).andExpect(content().bytes(release.bytes()));
        }
        mvc.perform(get("/api/v1/desktop/skills/"+skill.getId()).with(user(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.data.latestRelease.sequence").value(2)).andExpect(jsonPath("$.data.latestRelease.requiredMcps").isEmpty());
        assertThat(skills.findById(skill.getId()).orElseThrow().getVersion()).isEqualTo(11);
    }
    @Test void every_release_and_package_request_rechecks_visibility_and_download_permission() throws Exception {
        var release=published("zip",1);
        skill.setPublicScope("PRIVATE"); skills.saveAndFlush(skill);
        for(String suffix:List.of("","/package"))mvc.perform(get(route(release)+suffix).with(user(viewer))).andExpect(status().isNotFound());
        skill.setPublicScope("PUBLIC"); skill.setApprovalStatus("DRAFT"); skills.saveAndFlush(skill);
        for(String suffix:List.of("","/package"))mvc.perform(get(route(release)+suffix).with(user(admin))).andExpect(status().isForbidden()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get(route(release)).with(user(creator))).andExpect(status().isOk());
    }
    @Test void withdrawn_missing_changed_and_traversal_artifacts_fail_explicitly() throws Exception {
        var release=published("zip",1);
        jdbc.update("UPDATE desktop_release SET withdrawn=true WHERE id=?",release.id());
        for(String suffix:List.of("","/package"))mvc.perform(get(route(release)+suffix).with(user(viewer))).andExpect(status().isGone()).andExpect(jsonPath("$.errorCode").value("RELEASE_WITHDRAWN"));
        jdbc.update("UPDATE desktop_release SET withdrawn=false WHERE id=?",release.id());
        Files.delete(storage.resolve(release.key()+".pkg"));
        mvc.perform(get(route(release)+"/package").with(user(viewer))).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        Files.write(storage.resolve(release.key()+".pkg"),new byte[release.bytes().length]);
        mvc.perform(get(route(release)+"/package").with(user(viewer))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("PACKAGE_UNAVAILABLE"));
        mvc.perform(get("/api/v1/desktop/skills/"+skill.getId()+"/releases/not-a-release/package").with(user(viewer))).andExpect(status().isNotFound());
    }
    @Test void database_uniqueness_keeps_published_ids_and_sequence_stable() throws Exception {
        var release=published("zip",1);
        String original=jdbc.queryForObject("SELECT metadata_json FROM desktop_release WHERE id=?",String.class,release.id());
        assertThatThrownBy(()->jdbc.update("INSERT INTO desktop_release(id,skill_id,sequence,metadata_json,package_id,withdrawn) VALUES(?,?,?,?,?,false)",release.id(),skill.getId(),2,"{}",release.packageId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO desktop_release(id,skill_id,sequence,metadata_json,package_id,withdrawn) VALUES(?,?,?,?,?,false)",UUID.randomUUID(),skill.getId(),1,"{}",release.packageId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT metadata_json FROM desktop_release WHERE id=?",String.class,release.id())).isEqualTo(original);
        assertThat(releases.latest(skill.getId()).orElseThrow().metadata().id()).isEqualTo(release.id().toString());
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void verified_stream_blocks_writes_until_its_handle_is_closed() throws Exception {
        var release=published("zip",1);
        var body=artifacts.open(releases.find(skill.getId(),release.id().toString()).orElseThrow().artifact());
        try(var stream=body.getInputStream()) {
            assertThatThrownBy(()->Files.write(storage.resolve(release.key()+".pkg"),new byte[release.bytes().length])).isInstanceOf(java.io.IOException.class);
            assertThat(stream.readAllBytes()).isEqualTo(release.bytes());
        }
        Files.write(storage.resolve(release.key()+".pkg"),new byte[release.bytes().length]);
        mvc.perform(get(route(release)+"/package").with(user(viewer))).andExpect(status().isServiceUnavailable());
    }
}

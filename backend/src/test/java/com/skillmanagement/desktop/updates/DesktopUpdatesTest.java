package com.skillmanagement.desktop.updates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmanagement.desktop.repository.DesktopCatalogRepository;
import com.skillmanagement.model.Role;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security, MVC, ACL and SQL queries against a private in-memory database. */
@SpringBootTest(properties={"knox.mail.system-id=", "spring.datasource.url=jdbc:h2:mem:desktopupdates;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"})
@AutoConfigureMockMvc
@ActiveProfiles({"test","desktop-test"})
@Transactional
class DesktopUpdatesTest {
    private static final String ROUTE="/api/v1/desktop/updates/check";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired SkillRepository skills;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DesktopCatalogRepository catalog;
    User viewer,creator,admin;
    Skill skill;

    @BeforeEach void fixtures() {
        // Fresh database for this test context; never use or reset the shared Store.
        for(String script:List.of("V1__desktop_sources.sql","V2__desktop_releases.sql")) {
            if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM information_schema.tables WHERE table_name='DESKTOP_RELEASE'",Boolean.class))) {
                new ResourceDatabasePopulator(new ClassPathResource("db/desktop-migration/"+script)).execute(jdbc.getDataSource());
            }
        }
        var role=roles.save(Role.builder().name("UPDATES_VIEWER").build());
        var elevated=roles.findByName("ADMIN_USER").orElseGet(()->roles.save(Role.builder().name("ADMIN_USER").build()));
        viewer=account("updates-viewer",role); creator=account("updates-creator",role); admin=account("updates-admin",elevated);
        skill=skills.saveAndFlush(Skill.builder().name("Updates synthetic").description("metadata").createdBy(creator.getId()).publicScope("PUBLIC").approvalStatus("APPROVED").version(999).build());
    }
    @AfterEach void removeCommittedFixtures() {
        if(TestTransaction.isActive())return; // Ordinary cases roll back their own fixtures.
        // Only the nontransactional regression commits data, in this private H2 database.
        var ids=jdbc.queryForList("SELECT id FROM skills_master WHERE created_by=?",Long.class,creator.getId());
        for(var id:ids) {
            var packages=jdbc.queryForList("SELECT package_id FROM desktop_release WHERE skill_id=?",UUID.class,id);
            jdbc.update("DELETE FROM desktop_release WHERE skill_id=?",id);
            for(var pkg:packages)jdbc.update("DELETE FROM desktop_package WHERE id=?",pkg);
            skills.deleteById(id);
        }
        users.deleteAllById(List.of(viewer.getId(),creator.getId(),admin.getId()));
        roles.deleteById(viewer.getRole().getId());
    }
    private User account(String name,Role role) {
        return users.saveAndFlush(User.builder().name(name).email(name+"@example.invalid").knoxId(name).password("unused").role(role).build());
    }
    private String release(Skill owner,long sequence,String version,boolean withdrawn) throws Exception {
        var id=UUID.randomUUID(); var pkg=UUID.randomUUID();
        jdbc.update("INSERT INTO desktop_package(id,format,file_name,size_bytes,sha256,storage_key) VALUES(?,?,?,?,?,?)",pkg,"zip","synthetic.zip",42,"a".repeat(64),UUID.randomUUID());
        var data=new LinkedHashMap<String,Object>();
        data.put("skillId",owner.getId().toString()); data.put("id",id.toString()); data.put("sequence",sequence); data.put("version",version);
        data.put("installName","updates-synthetic"); data.put("publishedAt","2026-10-17T00:00:00Z"); data.put("changelog","Synthetic");
        data.put("compatibleAgents",null); data.put("requiredMcps",List.of("private-mcp"));
        jdbc.update("INSERT INTO desktop_release(id,skill_id,sequence,metadata_json,package_id,withdrawn) VALUES(?,?,?,?,?,?)",id,owner.getId(),sequence,mapper.writeValueAsString(data),pkg,withdrawn);
        return id.toString();
    }
    private Map<String,String> pair(Skill owner,String release) { return Map.of("skillId",owner.getId().toString(),"releaseId",release); }
    private JsonNode check(User principal,List<?> items) throws Exception {
        var response=mvc.perform(post(ROUTE).with(user(principal)).contentType("application/json").content(mapper.writeValueAsBytes(Map.of("items",items))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn().getResponse();
        return mapper.readTree(response.getContentAsByteArray()).get("data");
    }
    private void invalid(String body) throws Exception {
        mvc.perform(post(ROUTE).with(user(viewer)).contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test void existing_chain_requires_authentication_including_invalid_or_expired_bearer() throws Exception {
        for(String bearer:List.of("","Bearer invalid-token")) {
            mvc.perform(post(ROUTE).header("Authorization",bearer).contentType("application/json").content("{\"items\":[]}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        }
    }
    @Test void accepts_one_and_exactly_one_hundred_unique_pairs_in_input_order() throws Exception {
        String current=release(skill,1,"9.0",false);
        assertThat(check(viewer,List.of(pair(skill,current))).get("items").size()).isEqualTo(1);
        List<Map<String,String>> input=new ArrayList<>();
        for(int i=0;i<100;i++)input.add(pair(skill,UUID.randomUUID().toString()));
        var data=check(viewer,input);
        assertThat(data.get("items").size()).isEqualTo(100);
        for(int i=0;i<100;i++) {
            assertThat(data.at("/items/"+i+"/currentReleaseId").asText()).isEqualTo(input.get(i).get("releaseId"));
            assertThat(data.at("/items/"+i+"/status").asText()).isEqualTo("unknown_release");
            assertThat(data.get("items").get(i).has("latestRelease")).isFalse();
        }
        assertThat(Instant.parse(data.get("checkedAt").asText())).isBeforeOrEqualTo(Instant.now());
    }
    @Test void rejects_zero_101_and_duplicate_pairs_but_not_same_skill_with_different_releases() throws Exception {
        invalid("{\"items\":[]}");
        List<Map<String,String>> input=new ArrayList<>();
        for(int i=0;i<101;i++)input.add(pair(skill,UUID.randomUUID().toString()));
        invalid(mapper.writeValueAsString(Map.of("items",input)));
        var same=pair(skill,UUID.randomUUID().toString());
        invalid(mapper.writeValueAsString(Map.of("items",List.of(same,same))));
        assertThat(check(viewer,input.subList(0,2)).get("items").size()).isEqualTo(2);
    }
    @Test void rejects_malformed_missing_non_string_and_extra_fields_without_echoing_local_data() throws Exception {
        for(String body:List.of("null","{}","[]","{","{\"items\":null}","{\"items\":{}}","{\"items\":[null]}",
            "{\"items\":[{}]}","{\"items\":[{\"skillId\":1,\"releaseId\":\"x\"}]}",
            "{\"items\":[{\"skillId\":\"1\",\"releaseId\":1}]}",
            "{\"items\":[{\"skillId\":\"0\",\"releaseId\":\"x\"}]}",
            "{\"items\":[{\"skillId\":\"9223372036854775808\",\"releaseId\":\"x\"}]}",
            "{\"items\":[{\"skillId\":\"1\",\"releaseId\":\" \"}]}",
            "{\"items\":[],\"targetPath\":\"private\"}",
            "{\"items\":[{\"skillId\":\"1\",\"releaseId\":\"x\",\"mcpToken\":\"private\"}]}"))invalid(body);
    }
    @Test void uses_sequence_not_version_or_business_metadata_and_returns_only_release_reference() throws Exception {
        String older=release(skill,1,"99.0",false),latest=release(skill,2,"0.1",false);
        var before=check(viewer,List.of(pair(skill,older),pair(skill,latest)));
        assertThat(before.at("/items/0/status").asText()).isEqualTo("update_available");
        assertThat(before.at("/items/1/status").asText()).isEqualTo("up_to_date");
        assertThat(before.at("/items/0/latestRelease/id").asText()).isEqualTo(latest);
        assertThat(before.at("/items/0/latestRelease/sequence").asLong()).isEqualTo(2);
        assertThat(before.at("/items/0/latestRelease/version").asText()).isEqualTo("0.1");
        assertThat(before.at("/items/0/latestRelease").size()).isEqualTo(3);
        assertThat(before.at("/items/0/skillId").asText()).isEqualTo(skill.getId().toString());
        skill.setVersion(1000); skill.setDescription("changed metadata only"); skills.saveAndFlush(skill);
        assertThat(check(viewer,List.of(pair(skill,latest))).at("/items/0")).isEqualTo(before.at("/items/1"));
        assertThat(before.toString()).doesNotContain("package","requiredMcps","private-mcp","targetPath","sha256","downloadPath");
    }
    @Test void revoked_visibility_precedes_release_lookup_and_does_not_poison_other_results() throws Exception {
        String known=release(skill,1,"1",false);
        skill.setPublicScope("PRIVATE"); skills.saveAndFlush(skill);
        // Corrupt metadata would fail if read before ACL.
        jdbc.update("UPDATE desktop_release SET metadata_json='{}' WHERE id=?",UUID.fromString(known));
        var data=check(viewer,List.of(pair(skill,known),pair(skill,UUID.randomUUID().toString()),Map.of("skillId","9223372036854775807","releaseId",known)));
        for(var item:data.get("items")) {
            assertThat(item.get("status").asText()).isEqualTo("unavailable");
            assertThat(item.has("latestRelease")).isFalse();
        }
    }
    @Test void current_download_acl_matches_pilot_for_creator_admin_and_ordinary_viewer() throws Exception {
        String known=release(skill,1,"1",false);
        skill.setApprovalStatus("DRAFT"); skills.saveAndFlush(skill);
        for(var person:List.of(creator,admin,viewer)) {
            boolean permitted;
            try { permitted=DesktopCatalogRepository.canDownload(catalog.visible(skill.getId(),person),person); }
            catch(BusinessException denied) { permitted=false; }
            var item=check(person,List.of(pair(skill,known))).at("/items/0");
            assertThat(item.get("status").asText()).isEqualTo(permitted?"up_to_date":"unavailable");
            assertThat(item.has("latestRelease")).isEqualTo(permitted);
        }
    }
    @Test void withdrawal_excludes_latest_and_withdrawn_current_is_unavailable_even_with_newer_active() throws Exception {
        String first=release(skill,1,"first",false),second=release(skill,2,"second",false),third=release(skill,3,"third",true);
        assertThat(check(viewer,List.of(pair(skill,first))).at("/items/0/latestRelease/id").asText()).isEqualTo(second);
        jdbc.update("UPDATE desktop_release SET withdrawn=true WHERE id=?",UUID.fromString(first));
        var item=check(viewer,List.of(pair(skill,first))).at("/items/0");
        assertThat(item.get("status").asText()).isEqualTo("unavailable");
        assertThat(item.has("latestRelease")).isFalse();
        item=check(viewer,List.of(pair(skill,second))).at("/items/0");
        assertThat(item.get("status").asText()).isEqualTo("up_to_date");
        assertThat(item.at("/latestRelease/id").asText()).isEqualTo(second);
        assertThat(check(viewer,List.of(pair(skill,third))).at("/items/0/status").asText()).isEqualTo("unavailable");
    }
    @Test void release_from_another_skill_is_unknown_and_never_leaks_latest() throws Exception {
        String current=release(skill,1,"1",false);
        var other=skills.saveAndFlush(Skill.builder().name("Other").createdBy(creator.getId()).publicScope("PUBLIC").approvalStatus("APPROVED").build());
        var item=check(viewer,List.of(pair(other,current))).at("/items/0");
        assertThat(item.get("status").asText()).isEqualTo("unknown_release");
        assertThat(item.has("latestRelease")).isFalse();
    }
    @Test void corrupt_authorized_release_is_http_failure_instead_of_false_up_to_date() throws Exception {
        String current=release(skill,1,"1",false);
        jdbc.update("UPDATE desktop_release SET metadata_json='{}' WHERE id=?",UUID.fromString(current));
        mvc.perform(post(ROUTE).with(user(viewer)).contentType("application/json").content(mapper.writeValueAsBytes(Map.of("items",List.of(pair(skill,current))))))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("RELEASE_UNAVAILABLE"));
    }
    @Test void metadata_check_does_not_read_package_files_or_write_any_rows() throws Exception {
        String current=release(skill,1,"1",false);
        // No archive exists for these DB records. A metadata check must still succeed.
        var before=jdbc.queryForList("SELECT * FROM desktop_release ORDER BY id");
        var packages=jdbc.queryForList("SELECT * FROM desktop_package ORDER BY id");
        check(viewer,List.of(pair(skill,current)));
        assertThat(jdbc.queryForList("SELECT * FROM desktop_release ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM desktop_package ORDER BY id")).isEqualTo(packages);
        assertThat(skills.findById(skill.getId()).orElseThrow().getVersion()).isEqualTo(999);
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void committed_acl_revocation_and_mixed_batch_finish_the_service_transaction() throws Exception {
        String known=release(skill,1,"1",false);
        assertThat(check(viewer,List.of(pair(skill,known))).at("/items/0/status").asText()).isEqualTo("up_to_date");
        skill.setPublicScope("PRIVATE"); skills.saveAndFlush(skill);
        var visible=skills.saveAndFlush(Skill.builder().name("Still authorized").createdBy(creator.getId()).publicScope("PUBLIC").approvalStatus("APPROVED").build());
        String active=release(visible,1,"1",false);
        var data=check(viewer,List.of(pair(skill,known),pair(skill,UUID.randomUUID().toString()),pair(visible,active)));
        assertThat(data.at("/items/0/status").asText()).isEqualTo("unavailable");
        assertThat(data.at("/items/1/status").asText()).isEqualTo("unavailable");
        assertThat(data.at("/items/2/status").asText()).isEqualTo("up_to_date");
        assertThat(data.at("/items/2/latestRelease/id").asText()).isEqualTo(active);
        assertThat(data.at("/items/0").has("latestRelease")).isFalse();
        assertThat(data.at("/items/1").has("latestRelease")).isFalse();
    }
}

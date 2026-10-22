package com.skillmanagement.desktop;

import com.skillmanagement.desktop.repository.DesktopCatalogRepository;
import com.skillmanagement.model.Role;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"knox.mail.system-id=", "spring.datasource.url=jdbc:h2:mem:desktopcatalog;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"})
@AutoConfigureMockMvc
@ActiveProfiles({"test","desktop-test"})
@Transactional
class DesktopCatalogTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SkillRepository skills;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired OrganizationRepository organizations;
    @Autowired DesktopCatalogRepository desktop;
    @Autowired SkillAccessHelper access;
    User viewer, creator, admin;

    @BeforeEach void fixtures() throws Exception {
        DesktopTestSchema.create(jdbc);
        jdbc.update("DELETE FROM desktop_skill_source");
        jdbc.update("DELETE FROM desktop_source");
        jdbc.update("INSERT INTO desktop_source VALUES ('quality','Quality'),('security','Security')");
        var ordinary=roles.save(Role.builder().name("DESKTOP_TEST_USER").build());
        var elevated=roles.findByName("ADMIN_USER").orElseGet(()->roles.save(Role.builder().name("ADMIN_USER").build()));
        viewer=account("desktop-viewer",ordinary);
        creator=account("desktop-creator",ordinary);
        admin=account("desktop-admin",elevated);
    }
    User account(String name,Role role) {
        return users.saveAndFlush(User.builder().name(name).email(name+"@example.invalid").knoxId(name).password("not-used-for-authentication").role(role).build());
    }
    Skill skill(String name,String description,User owner,String scope,String status,String source) {
        var skill=skills.saveAndFlush(Skill.builder().name(name).description(description).createdBy(owner.getId()).publicScope(scope).approvalStatus(status).hashtags(List.of("pilot")).version(7).build());
        if(source!=null)jdbc.update("INSERT INTO desktop_skill_source VALUES (?,?)",skill.getId(),source);
        return skill;
    }
    void organization(User person,String part,String group,String employed) {
        var row=new OrganizationMaster(); row.setKnoxId(person.getKnoxId()); row.setUserName(person.getName()); row.setPart(part); row.setGroupName(group); row.setEmployeeNumber(person.getId().toString()); row.setRegularPartner("Regular"); row.setEmployed(employed);
        organizations.saveAndFlush(row);
    }

    @Test void unauthenticated_requests_use_existing_security_chain() throws Exception {
        mvc.perform(get("/api/v1/desktop/skills")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void acl_and_combined_filters_run_before_count_and_name_id_pagination() throws Exception {
        skill("000 hidden","needle",creator,"PRIVATE","APPROVED","quality");
        var first=skill("alpha","needle",creator,"PUBLIC","APPROVED","quality");
        var second=skill("Alpha","needle",viewer,"PRIVATE","DRAFT","quality");
        skill("beta","needle",creator,"PUBLIC","APPROVED","security");
        skill("aardvark","different",creator,"PUBLIC","APPROVED","quality");
        mvc.perform(get("/api/v1/desktop/skills").with(user(viewer)).param("q","needle").param("sourceId","quality").param("pageSize","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(2)).andExpect(jsonPath("$.data.items[0].id").value(first.getId().toString())).andExpect(jsonPath("$.data.items[0].latestRelease").isEmpty());
        mvc.perform(get("/api/v1/desktop/skills").with(user(viewer)).param("q","needle").param("sourceId","quality").param("pageSize","1").param("page","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(second.getId().toString()));
        assertThat(desktop.search(viewer,"needle","quality",1,20).getTotalElements()).isEqualTo(2);
    }

    @Test void visibility_matches_upstream_helper_and_download_rules_match_existing_controller() {
        var own=skill("own draft","",viewer,"PRIVATE","DRAFT",null);
        var publicSkill=skill("public","",creator,"PUBLIC","APPROVED",null);
        var privateSkill=skill("private","",creator,"PRIVATE","APPROVED",null);
        var draft=skill("other draft","",creator,"PUBLIC","DRAFT",null);
        var pending=skill("pending","",creator,"PUBLIC","PENDING_APPROVAL",null);
        organization(viewer,"Engineering","Platform","Y");
        organization(creator,"engineering","platform","Y");
        var part=skill("part","",creator,"PART","APPROVED",null);
        var group=skill("group","",creator,"GROUP","APPROVED",null);
        var former=account("desktop-former",viewer.getRole());
        organization(former,"Engineering","Platform","N");
        var inactive=skill("former employee part","",former,"PART","APPROVED",null);
        var deleted=skill("deleted","",viewer,"PUBLIC","APPROVED",null);
        deleted.setDeletedYn("Y"); skills.saveAndFlush(deleted);
        for(var principal:List.of(viewer,admin)) {
            var visible=desktop.search(principal,"",null,1,100).getContent().stream().map(Skill::getId).toList();
            for(var entry:List.of(own,publicSkill,privateSkill,draft,pending,part,group,inactive,deleted)) {
                boolean expected;
                try { access.getVisible(entry.getId(),principal); expected=true; } catch(BusinessException denied) { expected=false; }
                assertThat(visible.contains(entry.getId())).as("visibility %s / %s",principal.getName(),entry.getName()).isEqualTo(expected);
            }
            if(principal==viewer)assertThat(visible).contains(part.getId(),group.getId()).doesNotContain(inactive.getId(),deleted.getId());
        }
        assertThat(DesktopCatalogRepository.canDownload(own,viewer)).isTrue();
        assertThat(DesktopCatalogRepository.canDownload(draft,admin)).isFalse();
        assertThat(DesktopCatalogRepository.canDownload(pending,admin)).isTrue();
        assertThat(DesktopCatalogRepository.canDownload(publicSkill,viewer)).isTrue();
    }

    @Test void flat_detail_has_required_metadata_and_does_not_invent_a_release() throws Exception {
        var visible=skill("Skill without desktop release","# Description",creator,"PUBLIC","APPROVED","quality");
        var hidden=skill("Hidden","",creator,"PRIVATE","APPROVED",null);
        mvc.perform(get("/api/v1/desktop/skills/{id}",visible.getId()).with(user(viewer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(visible.getId().toString())).andExpect(jsonPath("$.data.metadataRevision").value(7))
            .andExpect(jsonPath("$.data.creator.id").value(creator.getId().toString())).andExpect(jsonPath("$.data.roles").isArray()).andExpect(jsonPath("$.data.tags[0]").value("pilot"))
            .andExpect(jsonPath("$.data.categoryIds").isArray()).andExpect(jsonPath("$.data.sourceIds[0]").value("quality")).andExpect(jsonPath("$.data.canDownload").value(true))
            .andExpect(jsonPath("$.data.latestRelease").isEmpty()).andExpect(jsonPath("$.data.compatibleAgents").isEmpty()).andExpect(jsonPath("$.data.updatedAt").isString());
        mvc.perform(get("/api/v1/desktop/skills/{id}",hidden.getId()).with(user(viewer))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/desktop/me").with(user(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(viewer.getId().toString())).andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test void invalid_queries_are_explicit_and_modes_are_unavailable() throws Exception {
        for(var mode:List.of("trending","hot"))mvc.perform(get("/api/v1/desktop/skills").with(user(viewer)).param("mode",mode)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errorCode").value("MODE_UNAVAILABLE"));
        mvc.perform(get("/api/v1/desktop/skills").with(user(viewer)).param("mode","invented")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/desktop/skills").with(user(viewer)).param("sourceId","invented")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/desktop/skills").with(user(viewer)).param("page","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/desktop/skills").with(user(viewer)).param("pageSize","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/desktop/taxonomy").with(user(viewer))).andExpect(status().isOk()).andExpect(jsonPath("$.data.catalogModes[1].available").value(false));
    }

    @Test void search_treats_percent_and_underscore_as_literal_text() {
        var expected=skill("literal 100%_done","",creator,"PUBLIC","APPROVED",null);
        skill("literal 100percentXdone","",creator,"PUBLIC","APPROVED",null);
        assertThat(desktop.search(viewer,"100%_",null,1,20).getContent()).extracting(Skill::getId).containsExactly(expected.getId());
    }
}

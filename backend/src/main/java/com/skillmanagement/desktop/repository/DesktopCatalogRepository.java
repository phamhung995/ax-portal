package com.skillmanagement.desktop.repository;

import com.skillmanagement.desktop.dto.DesktopDtos;
import com.skillmanagement.desktop.service.DesktopFailure;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.user.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Repository
@Profile({"desktop-pilot","desktop-test"})
@Transactional(readOnly=true)
public class DesktopCatalogRepository {
    private final SkillRepository skills;
    private final SkillAccessHelper access;
    private final JdbcTemplate jdbc;
    public DesktopCatalogRepository(SkillRepository skills,SkillAccessHelper access,JdbcTemplate jdbc) {
        this.skills=skills; this.access=access; this.jdbc=jdbc;
    }
    public Page<Skill> search(User viewer,String q,String sourceId,int page,int pageSize) {
        Specification<Skill> visible=SkillRepository.visibleTo(viewer,access.viewerParts(viewer),access.viewerGroups(viewer));
        if(!q.isBlank()) {
            String pattern="%"+q.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
            visible=visible.and((root,query,cb)->cb.or(cb.like(cb.lower(root.get("name")),pattern,'\\'),cb.like(cb.lower(root.get("description")),pattern,'\\')));
        }
        if(sourceId!=null) {
            if(!sourceExists(sourceId))throw DesktopFailure.query();
            var ids=jdbc.queryForList("SELECT skill_id FROM desktop_skill_source WHERE source_id=?",Long.class,sourceId);
            visible=visible.and((root,query,cb)->ids.isEmpty()?cb.disjunction():root.get("id").in(ids));
        }
        var order=Sort.by(Sort.Order.asc("name").ignoreCase(),Sort.Order.asc("id"));
        return skills.findAll(visible,PageRequest.of(page-1,pageSize,order));
    }
    public Skill visible(long id,User viewer) { return access.getVisible(id,viewer); }
    public List<DesktopDtos.Source> sources() {
        return jdbc.query("SELECT id,label FROM desktop_source ORDER BY id",(row,index)->new DesktopDtos.Source(row.getString(1),row.getString(2)));
    }
    public List<String> sourcesFor(long skillId) { return jdbc.queryForList("SELECT source_id FROM desktop_skill_source WHERE skill_id=? ORDER BY source_id",String.class,skillId); }
    private boolean sourceExists(String id) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM desktop_source WHERE id=?",Boolean.class,id)); }
    public static boolean canDownload(Skill skill,User viewer) {
        if(skill.getCreatedBy().equals(viewer.getId()))return true;
        boolean admin=viewer.getRole()!=null && "ADMIN_USER".equals(viewer.getRole().getName());
        return Skill.STATUS_APPROVED.equals(skill.getApprovalStatus()) || admin && Skill.STATUS_PENDING_APPROVAL.equals(skill.getApprovalStatus());
    }
}

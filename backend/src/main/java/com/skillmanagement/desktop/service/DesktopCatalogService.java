package com.skillmanagement.desktop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillmanagement.desktop.dto.DesktopDtos;
import com.skillmanagement.desktop.repository.DesktopCatalogRepository;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.skill.repository.SkillCategoryMappingRepository;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Profile({"desktop-pilot","desktop-test"})
@Transactional(readOnly=true)
public class DesktopCatalogService {
    private final DesktopCatalogRepository catalog;
    private final UserRepository users;
    private final SkillCategoryMappingRepository categories;
    private final ObjectMapper mapper;
    private final DesktopReleaseService releases;
    public DesktopCatalogService(DesktopCatalogRepository catalog,UserRepository users,SkillCategoryMappingRepository categories,ObjectMapper mapper,DesktopReleaseService releases) {
        this.catalog=catalog; this.users=users; this.categories=categories; this.mapper=mapper;this.releases=releases;
    }
    public DesktopDtos.UserView me(User user) { return new DesktopDtos.UserView(user.getId().toString(),displayName(user)); }
    public DesktopDtos.Taxonomy taxonomy() {
        return new DesktopDtos.Taxonomy(catalog.sources(),List.of(new DesktopDtos.Mode("all_time","All Time",true),new DesktopDtos.Mode("trending","Trending",false),new DesktopDtos.Mode("hot","Hot",false)));
    }
    public DesktopDtos.Page<DesktopDtos.SkillSummary> search(User viewer,String q,String mode,String sourceId,int page,int pageSize) {
        if(q.length()>1024 || page<1 || pageSize<1 || pageSize>100 || (long)(page-1)*pageSize>Integer.MAX_VALUE || sourceId!=null && (sourceId.isBlank() || sourceId.length()>256))throw DesktopFailure.query();
        if(!List.of("all_time","trending","hot").contains(mode))throw DesktopFailure.query();
        if(!mode.equals("all_time"))throw new DesktopFailure(422,"MODE_UNAVAILABLE","This catalog mode is not available for the pilot.");
        var result=catalog.search(viewer,q.trim(),sourceId,page,pageSize);
        return new DesktopDtos.Page<>(result.getContent().stream().map(skill->summary(skill,viewer)).toList(),page,pageSize,result.getTotalElements());
    }
    public Map<String,Object> detail(long id,User viewer) {
        var skill=catalog.visible(id,viewer);
        Map<String,Object> result=mapper.convertValue(summary(skill,viewer),new TypeReference<>() {});
        result.put("storePageUrl",null);
        result.put("latestRelease",releases.latest(skill,viewer).orElse(null));
        return result;
    }
    private DesktopDtos.SkillSummary summary(Skill skill,User viewer) {
        var creator=users.findById(skill.getCreatedBy()).map(this::me).orElseGet(()->new DesktopDtos.UserView(skill.getCreatedBy().toString(),"Unknown creator"));
        var categoryIds=categories.findBySkillId(skill.getId()).stream().map(mapping->mapping.getCategoryId().toString()).sorted().toList();
        var latest=releases.latest(skill,viewer).map(DesktopDtos.Release::reference).orElse(null);
        // Business version is metadata revision, never an installable desktop release.
        return new DesktopDtos.SkillSummary(skill.getId().toString(),skill.getName(),skill.getDescription()==null?"":skill.getDescription(),Math.max(0,skill.getVersion()),
            List.of(),skill.getHashtags()==null?List.of():List.copyOf(skill.getHashtags()),categoryIds,catalog.sourcesFor(skill.getId()),creator,null,DesktopCatalogRepository.canDownload(skill,viewer),skill.getUpdatedAt(),latest);
    }
    private String displayName(User user) { return user.getName()==null || user.getName().isBlank()?"User "+user.getId():user.getName(); }
}

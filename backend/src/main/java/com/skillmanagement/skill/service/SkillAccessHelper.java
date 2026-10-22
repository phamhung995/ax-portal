package com.skillmanagement.skill.service;

import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.user.model.User;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.skill.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared visibility helper: loads a skill and enforces the visibility rule
 * (status + scope) for the viewer. Not-visible skills return 404 SKILL_NOT_FOUND
 * so their existence is not revealed (Detail SRS TC-38).
 */
@Component
@RequiredArgsConstructor
public class SkillAccessHelper {

    private final SkillRepository skillRepository;
    private final OrganizationRepository organizationRepository;

    /**
     * Returns the skill if it exists and is visible to the viewer,
     * otherwise throws 404 SKILL_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public Skill getVisible(Long skillId, User viewer) {
        Skill skill = skillRepository.findById(skillId).orElse(null);
        if (skill == null || "Y".equals(skill.getDeletedYn())) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_FOUND, "Skill not found.");
        }
        Set<String> parts = viewerParts(viewer);
        Set<String> groups = viewerGroups(viewer);
        boolean visible = skillRepository.count(
                SkillRepository.visibleTo(viewer, parts, groups)
                        .and((root, query, cb) -> cb.equal(root.get("id"), skillId))) > 0;
        if (!visible) {
            throw new BusinessException(BusinessErrorCode.SKILL_NOT_FOUND, "Skill not found.");
        }
        return skill;
    }

    public Set<String> viewerParts(User viewer) {
        return orgRows(viewer).stream()
                .map(o -> o.getPart().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public Set<String> viewerGroups(User viewer) {
        return orgRows(viewer).stream()
                .map(o -> o.getGroupName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private List<OrganizationMaster> orgRows(User viewer) {
        return organizationRepository.findByKnoxIdIgnoreCase(viewer.getKnoxId()).stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .toList();
    }
}

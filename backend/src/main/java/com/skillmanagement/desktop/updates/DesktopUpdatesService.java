package com.skillmanagement.desktop.updates;

import com.skillmanagement.desktop.dto.DesktopDtos;
import com.skillmanagement.desktop.repository.DesktopCatalogRepository;
import com.skillmanagement.desktop.repository.DesktopReleaseRepository;
import com.skillmanagement.skill.repository.SkillRepository;
import com.skillmanagement.skill.service.SkillAccessHelper;
import com.skillmanagement.user.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.skillmanagement.desktop.updates.DesktopUpdatesDtos.*;

@Service
@Profile({"desktop-pilot","desktop-test"})
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class DesktopUpdatesService {
    private final SkillRepository skills;
    private final SkillAccessHelper access;
    private final DesktopReleaseRepository releases;

    public DesktopUpdatesService(SkillRepository skills,SkillAccessHelper access,DesktopReleaseRepository releases) {
        this.skills=skills; this.access=access; this.releases=releases;
    }

    public UpdateCheck check(Request request,User viewer) {
        var results=request.items().stream().map(item->check(item,viewer)).toList();
        return new UpdateCheck(Instant.now(),results);
    }

    private Item check(KnownRelease item,User viewer) {
        long skillId=Long.parseLong(item.skillId());
        // Same visibility specification as the pilot, returning absence rather than
        // catching getVisible's exception (which marks a joined transaction rollback-only).
        var visible=SkillRepository.visibleTo(viewer,access.viewerParts(viewer),access.viewerGroups(viewer))
            .and((root,query,cb)->cb.equal(root.get("id"),skillId));
        var skill=skills.findOne(visible);
        if(skill.isEmpty() || !DesktopCatalogRepository.canDownload(skill.get(),viewer))return result(item,Status.unavailable,null);

        // Never inspect release metadata before checking current visibility AND download ACL.
        var current=releases.find(skillId,item.releaseId());
        if(current.isEmpty())return result(item,Status.unknown_release,null);
        if(current.get().withdrawn())return result(item,Status.unavailable,null);
        var latest=releases.latest(skillId);
        if(latest.isEmpty())return result(item,Status.unavailable,null);
        var known=current.get().metadata();
        var candidate=latest.get().metadata();
        boolean newer=candidate.sequence()>known.sequence();
        var selected=newer?candidate:known;
        return result(item,newer?Status.update_available:Status.up_to_date,
            new DesktopDtos.ReleaseRef(selected.id(),selected.sequence(),selected.version()));
    }

    private Item result(KnownRelease item,Status status,DesktopDtos.ReleaseRef latest) {
        return new Item(item.skillId(),item.releaseId(),status,latest);
    }
}

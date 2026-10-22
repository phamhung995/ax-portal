package com.skillmanagement.desktop.service;

import com.skillmanagement.desktop.dto.DesktopDtos;
import com.skillmanagement.desktop.repository.DesktopCatalogRepository;
import com.skillmanagement.desktop.repository.DesktopReleaseRepository;
import com.skillmanagement.skill.model.Skill;
import com.skillmanagement.user.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Service
@Profile({"desktop-pilot","desktop-test"})
@Transactional(readOnly=true)
public class DesktopReleaseService {
    private final DesktopCatalogRepository catalog;
    private final DesktopReleaseRepository releases;
    public DesktopReleaseService(DesktopCatalogRepository catalog,DesktopReleaseRepository releases) { this.catalog=catalog;this.releases=releases; }
    public DesktopReleaseRepository.StoredRelease authorized(long skillId,String releaseId,User viewer) {
        var skill=catalog.visible(skillId,viewer);
        if(!DesktopCatalogRepository.canDownload(skill,viewer))throw new DesktopFailure(403,"DOWNLOAD_FORBIDDEN","This skill cannot be downloaded in its current status.");
        var release=releases.find(skillId,releaseId).orElseThrow(()->new DesktopFailure(404,"RELEASE_NOT_FOUND","The desktop release was not found."));
        if(release.withdrawn())throw new DesktopFailure(410,"RELEASE_WITHDRAWN","This desktop release has been withdrawn.");
        return release;
    }
    public Optional<DesktopDtos.Release> latest(Skill skill,User viewer) { return DesktopCatalogRepository.canDownload(skill,viewer)?releases.latest(skill.getId()).map(this::dto):Optional.empty(); }
    public DesktopDtos.Release dto(DesktopReleaseRepository.StoredRelease stored) {
        var release=stored.metadata();var artifact=stored.artifact();
        var path="/api/v1/desktop/skills/"+release.skillId()+"/releases/"+release.id()+"/package";
        return new DesktopDtos.Release(release.skillId(),release.id(),release.sequence(),release.version(),release.installName(),release.publishedAt(),release.changelog(),release.compatibleAgents(),release.requiredMcps(),new DesktopDtos.PackageInfo(artifact.format(),artifact.fileName(),artifact.sizeBytes(),artifact.sha256(),path));
    }
}

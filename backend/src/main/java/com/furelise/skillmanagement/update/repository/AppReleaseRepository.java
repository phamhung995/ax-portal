package com.furelise.skillmanagement.update.repository;

import com.furelise.skillmanagement.update.model.AppRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppReleaseRepository extends JpaRepository<AppRelease, Long> {

    /** Latest active release (newest published_at). */
    Optional<AppRelease> findFirstByIsActiveTrueOrderByPublishedAtDesc();

    /** Find by version (active only). */
    Optional<AppRelease> findByVersionAndIsActiveTrue(String version);

    /** Find by version (active OR inactive — for upsert/reactivate). */
    Optional<AppRelease> findByVersion(String version);

    /** All active releases, newest first. */
    List<AppRelease> findAllByIsActiveTrueOrderByPublishedAtDesc();

    boolean existsByVersion(String version);
}

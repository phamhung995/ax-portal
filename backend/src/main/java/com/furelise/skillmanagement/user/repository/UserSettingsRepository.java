package com.furelise.skillmanagement.user.repository;

import com.furelise.skillmanagement.user.model.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}

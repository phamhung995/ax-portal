package com.skillmanagement.auth.repository;

import com.skillmanagement.auth.model.LoginManagement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginRepository extends JpaRepository<LoginManagement, Long> {
}

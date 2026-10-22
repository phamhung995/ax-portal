package com.skillmanagement.repository;

import com.skillmanagement.model.EmailManagement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRepository extends JpaRepository<EmailManagement, Long> {
}

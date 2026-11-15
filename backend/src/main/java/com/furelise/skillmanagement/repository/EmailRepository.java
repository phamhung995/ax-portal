package com.furelise.skillmanagement.repository;

import com.furelise.skillmanagement.model.EmailManagement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRepository extends JpaRepository<EmailManagement, Long> {
}

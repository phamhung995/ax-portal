package com.skillmanagement.feedback.repository;

import com.skillmanagement.feedback.model.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FeedbackRepository
        extends JpaRepository<Feedback, Long>, JpaSpecificationExecutor<Feedback> {

    Page<Feedback> findByStatus(String status, Pageable pageable);

    Page<Feedback> findByTypeAndStatus(String type, String status, Pageable pageable);

    Page<Feedback> findByType(String type, Pageable pageable);

    Page<Feedback> findByUserId(Long userId, Pageable pageable);

    Page<Feedback> findAllBy(Pageable pageable);
}

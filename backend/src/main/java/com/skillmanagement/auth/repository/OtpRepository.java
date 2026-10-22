package com.skillmanagement.auth.repository;

import com.skillmanagement.auth.model.OtpManagement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<OtpManagement, Long> {

    /** Latest OTP for a (type, email) pair — only the latest is valid (SRS BR-09). */
    Optional<OtpManagement> findFirstByEmailIgnoreCaseAndOtpTypeOrderByOtpIdDesc(String email, String otpType);

    List<OtpManagement> findByEmailIgnoreCaseAndOtpTypeAndStatusOrderByOtpIdDesc(String email, String otpType, String status);

    void deleteByEmailIgnoreCaseAndOtpTypeAndStatus(String email, String otpType, String status);
}

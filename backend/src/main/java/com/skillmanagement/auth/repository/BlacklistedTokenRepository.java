package com.skillmanagement.auth.repository;

import com.skillmanagement.auth.model.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    boolean existsByJti(String jti);

    @Modifying
    @Query("DELETE FROM BlacklistedToken b WHERE b.expiryDate < :now")
    int deleteByExpiryDateBefore(@Param("now") Instant now);
}

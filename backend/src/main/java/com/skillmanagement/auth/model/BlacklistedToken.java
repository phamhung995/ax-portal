package com.skillmanagement.auth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "blacklisted_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String jti;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(name = "blacklisted_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant blacklistedAt = Instant.now();
}

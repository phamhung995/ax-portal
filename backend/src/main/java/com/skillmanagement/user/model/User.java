package com.skillmanagement.user.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.skillmanagement.model.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * JPA Entity representing a user in the system (USER_MASTER per SRS).
 * Implements UserDetails for Spring Security integration.
 * Lombok is retained here because JPA entities need mutable state,
 * which Java Records cannot provide.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "\"user\"", uniqueConstraints = {@UniqueConstraint(columnNames = "email")})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String gender;

    @Column(nullable = false)
    private String email;

    private String mobile;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /** Account status: ACTIVE / DEACTIVE (DEACTIVE users cannot login). */
    @Builder.Default
    @Column(nullable = false)
    private String status = "ACTIVE";

    /** Knox ID = local part of the email; links this user to organization_master. */
    @Column(name = "knox_id", nullable = false)
    private String knoxId;

    /** Login ID of the admin who created this user (null for self-registration). */
    @Column(name = "register_id")
    private String registerId;

    @Column(name = "created_ip")
    private String createdIp;

    @Column(name = "created_app_id")
    private String createdAppId;

    @Column(name = "updated_ip")
    private String updatedIp;

    @Column(name = "updated_app_id")
    private String updatedAppId;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = "ACTIVE";
        }
        if (knoxId == null && email != null && email.contains("@")) {
            knoxId = email.substring(0, email.indexOf('@'));
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = (role != null) ? role.toAuthority() : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

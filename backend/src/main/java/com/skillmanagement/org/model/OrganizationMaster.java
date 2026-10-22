package com.skillmanagement.org.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Organization master data imported from Excel (Organization Management module).
 * Composite PK (knox_id, employee_number, part, group_name):
 * one user (knox_id) can belong to multiple organizations.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "organization_master")
@IdClass(OrganizationMaster.OrganizationMasterId.class)
public class OrganizationMaster {

    @Id
    @Column(name = "knox_id", nullable = false)
    private String knoxId;

    @Column(name = "user_name", nullable = false)
    private String userName;

    /** 'Y' or 'N'. */
    @Column(nullable = false)
    private String employed;

    /** 'Regular' or 'Partner'. */
    @Column(name = "regular_partner", nullable = false)
    private String regularPartner;

    private String company;

    @Id
    @Column(nullable = false)
    private String part;

    @Id
    @Column(name = "group_name", nullable = false)
    private String groupName;

    @Id
    @Column(name = "employee_number", nullable = false)
    private String employeeNumber;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @PrePersist
    protected void onCreate() {
        if (importedAt == null) {
            importedAt = Instant.now();
        }
    }

    /** Composite primary key. */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrganizationMasterId implements Serializable {
        private static final long serialVersionUID = 1L;
        private String knoxId;
        private String employeeNumber;
        private String part;
        private String groupName;
    }
}

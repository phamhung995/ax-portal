package com.furelise.skillmanagement.org.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Organization master data imported from Excel (Organization Management module).
 * One person (knox_id) = one row. Hierarchy: Division > Center > Group > Part.
 * A row that stops at a higher level (e.g. Group but no Part) marks that
 * person as the manager of the lower levels under it.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "organization_master")
public class OrganizationMaster {

    @Id
    @Column(name = "knox_id", nullable = false, length = 100)
    private String knoxId;

    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    /** 'Y' or 'N'. */
    @Column(nullable = false)
    private String employed;

    /** 'Regular' or 'Partner'. */
    @Column(name = "regular_partner", nullable = false, length = 20)
    private String regularPartner;

    private String company;

    @Column(name = "division", length = 255)
    private String division;

    @Column(name = "center", length = 255)
    private String center;

    @Column(name = "group_name", length = 255)
    private String groupName;

    @Column(name = "part", length = 255)
    private String part;

    @Column(name = "employee_number", length = 50)
    private String employeeNumber;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    /** Job title in the company (e.g. Engineer, Professional). Display only. */
    @Column(name = "position", length = 255)
    private String position;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @PrePersist
    protected void onCreate() {
        if (importedAt == null) {
            importedAt = Instant.now();
        }
    }
}

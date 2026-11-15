package com.furelise.skillmanagement.org.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One person's assignment to one system, from the Human Resource List Excel
 * (sheet "Human Resource System List"). The jan..dec columns hold the
 * person's monthly input in month/month for the file's year.
 * The table is fully replaced on each import.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "hr_system_assignment")
public class HrSystemAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "knox_id", nullable = false, length = 100)
    private String knoxId;

    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    @Column(name = "employed", length = 10)
    private String employed;

    @Column(name = "regular_partner", length = 50)
    private String regularPartner;

    @Column(name = "company", length = 100)
    private String company;

    @Column(name = "part", length = 255)
    private String part;

    @Column(name = "employee_number", length = 50)
    private String employeeNumber;

    @Column(name = "system_key", nullable = false, length = 100)
    private String systemKey;

    @Column(name = "system_code", length = 100)
    private String systemCode;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "jan", precision = 5, scale = 2)
    private BigDecimal jan;

    @Column(name = "feb", precision = 5, scale = 2)
    private BigDecimal feb;

    @Column(name = "mar", precision = 5, scale = 2)
    private BigDecimal mar;

    @Column(name = "apr", precision = 5, scale = 2)
    private BigDecimal apr;

    @Column(name = "may", precision = 5, scale = 2)
    private BigDecimal may;

    @Column(name = "jun", precision = 5, scale = 2)
    private BigDecimal jun;

    @Column(name = "jul", precision = 5, scale = 2)
    private BigDecimal jul;

    @Column(name = "aug", precision = 5, scale = 2)
    private BigDecimal aug;

    @Column(name = "sep", precision = 5, scale = 2)
    private BigDecimal sep;

    @Column(name = "oct", precision = 5, scale = 2)
    private BigDecimal oct;

    @Column(name = "nov", precision = 5, scale = 2)
    private BigDecimal nov;

    @Column(name = "dec", precision = 5, scale = 2)
    private BigDecimal dec;

    @Column(name = "project_key", length = 100)
    private String projectKey;

    @Column(name = "project_name", length = 255)
    private String projectName;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
}

package com.furelise.skillmanagement.org.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A system from the System List Excel (sheet "Systems List").
 * The table is fully replaced on each import.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "system_master")
public class SystemMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_key", nullable = false, unique = true, length = 100)
    private String systemKey;

    @Column(name = "system_name", nullable = false, length = 255)
    private String systemName;

    @Column(name = "current_phase", length = 50)
    private String currentPhase;

    @Column(name = "client_of_sds", length = 255)
    private String clientOfSds;

    @Column(name = "gdc_group", length = 255)
    private String gdcGroup;

    @Column(name = "gdc_part", length = 255)
    private String gdcPart;

    @Column(name = "gdc_tpm_main", length = 255)
    private String gdcTpmMain;

    @Column(name = "project_key", length = 100)
    private String projectKey;

    @Column(name = "project_name", length = 255)
    private String projectName;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
}

package com.furelise.skillmanagement.org.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A sub system from the System List Excel (sheet "Sub Systems List").
 * Links to its system via system_key. The table is fully replaced on
 * each import.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "sub_system_master")
public class SubSystemMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_key", nullable = false, length = 100)
    private String systemKey;

    @Column(name = "sub_system_code", length = 100)
    private String subSystemCode;

    @Column(name = "sub_system_name", nullable = false, length = 255)
    private String subSystemName;

    @Column(name = "project_key", length = 100)
    private String projectKey;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;
}

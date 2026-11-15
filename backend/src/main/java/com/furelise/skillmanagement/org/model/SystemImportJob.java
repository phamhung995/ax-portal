package com.furelise.skillmanagement.org.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * One uploaded System List file awaiting (or finished) import.
 * Status lifecycle: PENDING -> RUNNING -> SUCCESS / FAILED.
 * A newer upload supersedes an older PENDING job (only the newest runs).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "system_import_job")
public class SystemImportJob {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "uploaded_by", nullable = false, length = 255)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "systems_count")
    private Integer systemsCount;

    @Column(name = "sub_systems_count")
    private Integer subSystemsCount;

    @Column(name = "error_count")
    private Integer errorCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_rows", columnDefinition = "jsonb")
    private List<SystemImportJobErrorRow> errorRows;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}

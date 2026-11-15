package com.furelise.skillmanagement.org.service.impl;

import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.org.dto.SystemImportResponse;
import com.furelise.skillmanagement.org.dto.SystemImportRowResult;
import com.furelise.skillmanagement.org.dto.SystemImportStatus;
import com.furelise.skillmanagement.org.model.SystemImportJob;
import com.furelise.skillmanagement.org.model.SystemImportJobErrorRow;
import com.furelise.skillmanagement.org.repository.SystemImportJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Upload + scheduled import of the System List Excel.
 *
 * <p>Flow: an admin uploads a file (stored on disk + a PENDING job row).
 * The daily scheduler (15:55, or an admin "Import Now") runs the newest PENDING
 * job: it reads the stored file and full-replaces the system tables in ONE
 * transaction, then records the result on the job row. Uploading a newer
 * file supersedes any older PENDING job (only the newest runs).
 */
@Slf4j
@Service
public class SystemImportJobService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final List<String> FINISHED_STATUSES = List.of(
            SystemImportJob.STATUS_SUCCESS, SystemImportJob.STATUS_FAILED);

    private final SystemImportJobRepository jobRepository;
    private final SystemImportServiceImpl importService;
    private final TransactionTemplate transactionTemplate;
    private final AuditService auditService;
    private final Path systemDir;

    /** Guards against concurrent runs (scheduler + "Import Now"). */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SystemImportJobService(SystemImportJobRepository jobRepository,
                                  SystemImportServiceImpl importService,
                                  PlatformTransactionManager transactionManager,
                                  AuditService auditService,
                                  @Value("${app.upload.system-dir:uploads/systems/}") String systemDirPath) {
        this.jobRepository = jobRepository;
        this.importService = importService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.auditService = auditService;
        this.systemDir = Paths.get(systemDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.systemDir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create system upload directory: " + systemDirPath, e);
        }
    }

    /**
     * Stores the uploaded file on disk and creates a PENDING job. Any older
     * PENDING jobs are marked SUPERSEDED (only the newest will run).
     *
     * @return the created job
     */
    @Transactional
    public SystemImportJob uploadFile(MultipartFile file, String adminLoginId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Please select a file.");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!original.endsWith(".xlsx") && !original.endsWith(".xls")) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID,
                    "Only .xlsx and .xls files are supported.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_TOO_LARGE, "File size is at most 10MB.");
        }

        // Stored with a .dat extension: the NASC kernel driver re-encrypts
        // Office files (.xlsx/.xls) the moment they are written to disk, which
        // would make the stored copy unreadable at import time. .dat files are
        // left clean, so the import always reads a real workbook. The original
        // file name is kept on the job row for display.
        String storedName = UUID.randomUUID() + ".dat";
        Path target = systemDir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Unable to store system file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to store the system file.");
        }

        // Supersede older PENDING jobs (only the newest upload runs).
        for (SystemImportJob pending : jobRepository.findByStatus(SystemImportJob.STATUS_PENDING)) {
            pending.setStatus(SystemImportJob.STATUS_SUPERSEDED);
            pending.setFinishedAt(Instant.now());
            jobRepository.save(pending);
        }

        SystemImportJob job = SystemImportJob.builder()
                .fileName(file.getOriginalFilename())
                .filePath(target.toString())
                .uploadedBy(adminLoginId)
                .uploadedAt(Instant.now())
                .status(SystemImportJob.STATUS_PENDING)
                .build();
        jobRepository.save(job);
        auditService.log("UPLOAD", "SYSTEMS", null, "file=" + file.getOriginalFilename());
        return job;
    }

    /** True when an import (scheduled or "Import Now") is currently running. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Runs the newest PENDING job (if any). No-op when nothing is pending or
     * another run is in progress. Safe to call from the scheduler and the
     * "Import Now" endpoint.
     *
     * @return the finished job, or null when nothing was run
     */
    public SystemImportJob runPendingImport(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("SYSTEMS-IMPORT skipped ({}): another run is in progress", trigger);
            return null;
        }
        try {
            SystemImportJob job = jobRepository
                    .findTopByStatusOrderByIdDesc(SystemImportJob.STATUS_PENDING)
                    .orElse(null);
            if (job == null) {
                log.info("SYSTEMS-IMPORT skipped ({}): no pending file", trigger);
                return null;
            }
            runJob(job, trigger);
            return jobRepository.findById(job.getId()).orElse(null);
        } finally {
            running.set(false);
        }
    }

    private void runJob(SystemImportJob job, String trigger) {
        job.setStatus(SystemImportJob.STATUS_RUNNING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);
        log.info("SYSTEMS-IMPORT started ({}): file={}", trigger, job.getFileName());
        try (InputStream in = Files.newInputStream(Path.of(job.getFilePath()))) {
            SystemImportResponse res = importService.doImport(in, job.getUploadedBy());
            job.setStatus(SystemImportJob.STATUS_SUCCESS);
            job.setSystemsCount(res.systemsCount());
            job.setSubSystemsCount(res.subSystemsCount());
            job.setErrorCount(res.errorCount());
            job.setErrorRows(res.errorRows().stream()
                    .map(r -> new SystemImportJobErrorRow(r.rowNumber(), r.sheet(), r.systemKey(), r.errors()))
                    .toList());
            auditService.log("IMPORT", "SYSTEMS", null,
                    "trigger=" + trigger + ", systems=" + res.systemsCount()
                            + ", subSystems=" + res.subSystemsCount()
                            + ", errors=" + res.errorCount());
        } catch (Exception e) {
            log.error("SYSTEMS-IMPORT failed ({}): file={}", trigger, job.getFileName(), e);
            job.setStatus(SystemImportJob.STATUS_FAILED);
            job.setErrorMessage(e.getMessage() == null ? "Import failed." :
                    e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000)));
        } finally {
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    /** The newest finished job (for the "last import" line). */
    @Transactional(readOnly = true)
    public SystemImportJob lastFinishedJob() {
        return jobRepository.findTopByStatusInOrderByIdDesc(FINISHED_STATUSES).orElse(null);
    }

    /** The newest PENDING job (for the "pending file" line). */
    @Transactional(readOnly = true)
    public SystemImportJob pendingJob() {
        return jobRepository.findTopByStatusOrderByIdDesc(SystemImportJob.STATUS_PENDING).orElse(null);
    }

    /** UI status: running flag + last finished job + pending file. */
    @Transactional(readOnly = true)
    public SystemImportStatus status() {
        return new SystemImportStatus(
                running.get(),
                toInfo(lastFinishedJob()),
                toInfo(pendingJob()));
    }

    private SystemImportStatus.SystemImportJobInfo toInfo(SystemImportJob job) {
        if (job == null) {
            return null;
        }
        return new SystemImportStatus.SystemImportJobInfo(
                job.getId(), job.getFileName(), job.getUploadedBy(), job.getUploadedAt(),
                job.getStatus(), job.getSystemsCount(), job.getSubSystemsCount(),
                job.getErrorCount(),
                job.getErrorRows() == null ? null : job.getErrorRows().stream()
                        .map(r -> new SystemImportRowResult(r.rowNumber(), r.sheet(), r.systemKey(), r.errors()))
                        .toList(),
                job.getErrorMessage(), job.getStartedAt(), job.getFinishedAt());
    }
}

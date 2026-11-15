package com.furelise.skillmanagement.org.service.impl;

import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.org.dto.OrgImportResponse;
import com.furelise.skillmanagement.org.dto.OrgImportRowResult;
import com.furelise.skillmanagement.org.dto.OrgImportStatus;
import com.furelise.skillmanagement.org.model.OrgImportJob;
import com.furelise.skillmanagement.org.model.OrgImportJobErrorRow;
import com.furelise.skillmanagement.org.repository.OrgImportJobRepository;
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
 * Upload + on-demand import of the Organization List Excel.
 *
 * <p>Flow: an admin uploads a file (stored on disk + a PENDING job row).
 * An admin "Import Now" (or a later scheduled run) runs the newest PENDING
 * job: it reads the stored file and scoped-replaces organization_master
 * (batched transactions), then records the result on the job row. Uploading
 * a newer file supersedes any older PENDING job (only the newest runs).
 *
 * <p>Unlike the HR/Systems imports (full replace), the organization import
 * keeps rows whose Knox ID is not in the file.
 */
@Slf4j
@Service
public class OrgImportJobService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final List<String> FINISHED_STATUSES = List.of(
            OrgImportJob.STATUS_SUCCESS, OrgImportJob.STATUS_FAILED);

    private final OrgImportJobRepository jobRepository;
    private final OrganizationServiceImpl importService;
    private final AuditService auditService;
    private final Path orgDir;

    /** Guards against concurrent runs ("Import Now" x2). */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OrgImportJobService(OrgImportJobRepository jobRepository,
                               OrganizationServiceImpl importService,
                               PlatformTransactionManager transactionManager,
                               AuditService auditService,
                               @Value("${app.upload.org-dir:uploads/org/}") String orgDirPath) {
        this.jobRepository = jobRepository;
        this.importService = importService;
        this.auditService = auditService;
        this.orgDir = Paths.get(orgDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.orgDir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create org upload directory: " + orgDirPath, e);
        }
    }

    /**
     * Stores the uploaded file on disk and creates a PENDING job. Any older
     * PENDING jobs are marked SUPERSEDED (only the newest will run).
     *
     * @return the created job
     */
    @Transactional
    public OrgImportJob uploadFile(MultipartFile file, String adminLoginId) {
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
        Path target = orgDir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Unable to store organization file: {}", target, e);
            throw new BusinessException(BusinessErrorCode.INTERNAL_ERROR,
                    "Unable to store the organization file.");
        }

        // Supersede older PENDING jobs (only the newest upload runs).
        for (OrgImportJob pending : jobRepository.findByStatus(OrgImportJob.STATUS_PENDING)) {
            pending.setStatus(OrgImportJob.STATUS_SUPERSEDED);
            pending.setFinishedAt(Instant.now());
            jobRepository.save(pending);
        }

        OrgImportJob job = OrgImportJob.builder()
                .fileName(file.getOriginalFilename())
                .filePath(target.toString())
                .uploadedBy(adminLoginId)
                .uploadedAt(Instant.now())
                .status(OrgImportJob.STATUS_PENDING)
                .build();
        jobRepository.save(job);
        auditService.log("UPLOAD", "ORGANIZATION", null, "file=" + file.getOriginalFilename());
        return job;
    }

    /** True when an import ("Import Now") is currently running. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Runs the newest PENDING job (if any). No-op when nothing is pending or
     * another run is in progress.
     *
     * @return the finished job, or null when nothing was run
     */
    public OrgImportJob runPendingImport(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("ORG-IMPORT skipped ({}): another run is in progress", trigger);
            return null;
        }
        try {
            OrgImportJob job = jobRepository
                    .findTopByStatusOrderByIdDesc(OrgImportJob.STATUS_PENDING)
                    .orElse(null);
            if (job == null) {
                log.info("ORG-IMPORT skipped ({}): no pending file", trigger);
                return null;
            }
            runJob(job, trigger);
            return jobRepository.findById(job.getId()).orElse(null);
        } finally {
            running.set(false);
        }
    }

    private void runJob(OrgImportJob job, String trigger) {
        job.setStatus(OrgImportJob.STATUS_RUNNING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);
        log.info("ORG-IMPORT started ({}): file={}", trigger, job.getFileName());
        try (InputStream in = Files.newInputStream(Path.of(job.getFilePath()))) {
            OrgImportResponse res = importService.doImport(in, job.getUploadedBy(), "job");
            job.setStatus(OrgImportJob.STATUS_SUCCESS);
            job.setRowsCount(res.totalRows());
            job.setSuccessCount(res.successCount());
            job.setErrorCount(res.errorCount());
            job.setMatchedCount(res.matchedCount());
            job.setDeactivatedCount(res.deactivatedCount());
            job.setErrorRows(res.rows().stream()
                    .filter(r -> "ERROR".equals(r.status()))
                    .map(r -> new OrgImportJobErrorRow(r.rowNumber(), r.knoxId(), r.userName(), r.errors()))
                    .toList());
            auditService.log("IMPORT", "ORGANIZATION", null,
                    "trigger=" + trigger + ", rows=" + res.totalRows()
                            + ", success=" + res.successCount()
                            + ", errors=" + res.errorCount());
        } catch (Exception e) {
            log.error("ORG-IMPORT failed ({}): file={}", trigger, job.getFileName(), e);
            job.setStatus(OrgImportJob.STATUS_FAILED);
            job.setErrorMessage(e.getMessage() == null ? "Import failed." :
                    e.getMessage().substring(0, Math.min(e.getMessage().length(), 1000)));
        } finally {
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    /** The newest finished job (for the "last import" line). */
    @Transactional(readOnly = true)
    public OrgImportJob lastFinishedJob() {
        return jobRepository.findTopByStatusInOrderByIdDesc(FINISHED_STATUSES).orElse(null);
    }

    /** The newest PENDING job (for the "pending file" line). */
    @Transactional(readOnly = true)
    public OrgImportJob pendingJob() {
        return jobRepository.findTopByStatusOrderByIdDesc(OrgImportJob.STATUS_PENDING).orElse(null);
    }

    /** UI status: running flag + last finished job + pending file. */
    @Transactional(readOnly = true)
    public OrgImportStatus status() {
        return new OrgImportStatus(
                running.get(),
                toInfo(lastFinishedJob()),
                toInfo(pendingJob()));
    }

    private OrgImportStatus.OrgImportJobInfo toInfo(OrgImportJob job) {
        if (job == null) {
            return null;
        }
        return new OrgImportStatus.OrgImportJobInfo(
                job.getId(), job.getFileName(), job.getUploadedBy(), job.getUploadedAt(),
                job.getStatus(), job.getRowsCount(), job.getSuccessCount(), job.getErrorCount(),
                job.getMatchedCount(), job.getDeactivatedCount(),
                job.getErrorRows() == null ? null : job.getErrorRows().stream()
                        .map(r -> OrgImportRowResult.error(r.rowNumber(), r.knoxId(), r.userName(), r.errors()))
                        .toList(),
                job.getErrorMessage(), job.getStartedAt(), job.getFinishedAt());
    }
}

package com.skillmanagement.org.service;

import com.skillmanagement.org.dto.OrgImportResponse;
import com.skillmanagement.org.dto.OrgSearchRequest;
import com.skillmanagement.org.dto.OrgSearchResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Organization Management (admin only).
 * Import organization master data from Excel and link it to users via Knox ID.
 */
public interface OrganizationService {

    OrgSearchResult search(OrgSearchRequest request);

    /**
     * Validates the Excel file, then SCOPED-REPLACES organization_master:
     * only the rows whose (knox_id, employee_number) appear in the file are
     * deleted and the SUCCESS rows are re-inserted (batched). Other rows
     * are kept. Matched users with no Employed=Y row are deactivated.
     */
    OrgImportResponse importExcel(MultipartFile file, String adminLoginId, String clientIp);

    /**
     * Builds the import template (header row + 2 sample rows) in memory and
     * returns it as an xlsx byte array (no disk write — NASC-safe).
     */
    byte[] downloadTemplate() throws java.io.IOException;

    /** Distinct Part values for the search filter. */
    List<String> partOptions();

    /** Distinct Group values for the search filter. */
    List<String> groupOptions();
}

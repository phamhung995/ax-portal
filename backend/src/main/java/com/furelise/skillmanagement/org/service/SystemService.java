package com.furelise.skillmanagement.org.service;

import com.furelise.skillmanagement.org.dto.SystemImportResponse;
import com.furelise.skillmanagement.org.dto.SystemSearchRequest;
import com.furelise.skillmanagement.org.dto.SystemSearchResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * System / Sub System master data (admin only).
 * Imported from the System List Excel (sheets "Systems List" +
 * "Sub Systems List"); the tables are fully replaced on each import.
 */
public interface SystemService {

    SystemSearchResult search(SystemSearchRequest request);

    /**
     * Validates the Excel file, then FULL-REPLACES system_master +
     * sub_system_master with the file's data (single transaction).
     * Systems with Current Phase = "End" are excluded (not an error).
     * Sub systems whose System Key is not among the valid systems are
     * reported as error rows; valid rows are still imported.
     */
    SystemImportResponse importExcel(MultipartFile file, String adminLoginId, String clientIp);

    /** Distinct GDC Part values for the search filter. */
    List<String> partOptions();
}

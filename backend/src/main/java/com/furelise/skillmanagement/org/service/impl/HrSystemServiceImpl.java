package com.furelise.skillmanagement.org.service.impl;

import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.org.dto.HrImportResponse;
import com.furelise.skillmanagement.org.dto.HrListItem;
import com.furelise.skillmanagement.org.dto.HrSearchRequest;
import com.furelise.skillmanagement.org.dto.HrSearchResult;
import com.furelise.skillmanagement.org.dto.SystemImportRowResult;
import com.furelise.skillmanagement.org.model.HrSystemAssignment;
import com.furelise.skillmanagement.org.repository.HrSystemAssignmentRepository;
import com.furelise.skillmanagement.org.service.HrSystemService;
import com.furelise.skillmanagement.user.service.NascFileDetector;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Human Resource System List implementation.
 * Import is a FULL REPLACE of hr_system_assignment in ONE transaction,
 * following the same pattern as the Systems import.
 */
@Service
public class HrSystemServiceImpl implements HrSystemService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    /** Rows flushed per batch (memory bound) inside the single import transaction. */
    private static final int BATCH_SIZE = 500;

    private static final String SHEET_HR = "Human Resource System List";

    /**
     * Normalized header names (whitespace removed, lower-case) of the column
     * names row. The sheet has a group row above the column names row; the
     * header scan below finds the column names row automatically.
     * "Group" in the file is stored as "part".
     */
    private static final List<String> HR_HEADERS = List.of(
            "knoxid", "username", "employedy/n", "regular/partner", "company",
            "group", "employeenumber", "systemkey", "systemcode", "role",
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec");
    private static final Map<String, String> HR_HEADER_DISPLAY = Map.ofEntries(
            Map.entry("knoxid", "Knox ID"),
            Map.entry("username", "User Name"),
            Map.entry("employedy/n", "Employed Y/N"),
            Map.entry("regular/partner", "Regular/ Partner"),
            Map.entry("company", "Company"),
            Map.entry("group", "Group (Part)"),
            Map.entry("employeenumber", "Employee Number"),
            Map.entry("systemkey", "System Key"),
            Map.entry("systemcode", "System Code"),
            Map.entry("role", "Role"),
            Map.entry("jan", "Jan"), Map.entry("feb", "Feb"), Map.entry("mar", "Mar"),
            Map.entry("apr", "Apr"), Map.entry("may", "May"), Map.entry("jun", "Jun"),
            Map.entry("jul", "Jul"), Map.entry("aug", "Aug"), Map.entry("sep", "Sep"),
            Map.entry("oct", "Oct"), Map.entry("nov", "Nov"), Map.entry("dec", "Dec"));

    private final HrSystemAssignmentRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final AuditService auditService;

    public HrSystemServiceImpl(HrSystemAssignmentRepository repository,
                               PlatformTransactionManager transactionManager,
                               AuditService auditService) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public HrSearchResult search(HrSearchRequest request) {
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : request.pageSize();

        Pageable pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.ASC, "knoxId", "systemKey"));
        Page<HrSystemAssignment> result = repository.findAll(buildSpecification(request), pageable);

        List<HrListItem> items = result.getContent().stream().map(m -> new HrListItem(
                m.getKnoxId(), m.getUserName(), m.getEmployed(), m.getRegularPartner(),
                m.getCompany(), m.getPart(), m.getEmployeeNumber(), m.getSystemKey(),
                m.getSystemCode(), m.getRole(), m.getJan(), m.getFeb(), m.getMar(),
                m.getApr(), m.getMay(), m.getJun(), m.getJul(), m.getAug(),
                m.getSep(), m.getOct(), m.getNov(), m.getDec(),
                m.getProjectKey(), m.getProjectName())).toList();
        return new HrSearchResult(result.getTotalElements(), page, pageSize, items);
    }

    private Specification<HrSystemAssignment> buildSpecification(HrSearchRequest r) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (notBlank(r.knoxId())) {
                predicates.add(cb.like(cb.lower(root.get("knoxId")),
                        "%" + r.knoxId().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.userName())) {
                predicates.add(cb.like(cb.lower(root.get("userName")),
                        "%" + r.userName().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.part())) {
                predicates.add(cb.like(cb.lower(root.get("part")),
                        "%" + r.part().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.systemKey())) {
                predicates.add(cb.like(cb.lower(root.get("systemKey")),
                        "%" + r.systemKey().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.role())) {
                predicates.add(cb.like(cb.lower(root.get("role")),
                        "%" + r.role().trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> partOptions() {
        return repository.findDistinctPart();
    }

    // ------------------------------------------------------------------
    // Import (full replace, batched flushes in ONE transaction)
    // ------------------------------------------------------------------

    @Override
    public HrImportResponse importExcel(MultipartFile file, String adminLoginId) {
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
        try (InputStream raw = file.getInputStream()) {
            return doImport(raw, adminLoginId);
        } catch (BusinessException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }
    }

    /**
     * Core import: parses the workbook from the given stream and full-replaces
     * hr_system_assignment in ONE transaction (readers keep seeing the old
     * data until commit; a failure rolls back to the old data). Shared by the
     * scheduled / "Import Now" job runner.
     */
    public HrImportResponse doImport(InputStream raw, String adminLoginId) {
        List<HrSystemAssignment> rows;
        List<SystemImportRowResult> errorRows;
        try {
            byte[] head = NascFileDetector.peekHead(raw);
            if (NascFileDetector.isNascMarker(head)) {
                throw new BusinessException(BusinessErrorCode.BULK_FILE_ENCRYPTED,
                        "File is encrypted by Samsung NASC document protection. "
                                + "Open the file in Excel, use Save As to a new file, and upload that one.");
            }
            try (Workbook workbook = WorkbookFactory.create(NascFileDetector.withHead(head, raw))) {
                Sheet sheet = findSheet(workbook, SHEET_HR);
                ParsedHr parsed = parseSheet(sheet);
                errorRows = parsed.errors();
                errorRows.sort(Comparator.comparingInt(SystemImportRowResult::rowNumber));

                Instant now = Instant.now();
                rows = parsed.rows().stream().map(p -> HrSystemAssignment.builder()
                        .knoxId(p.knoxId())
                        .userName(p.userName())
                        .employed(p.employed())
                        .regularPartner(p.regularPartner())
                        .company(p.company())
                        .part(p.part())
                        .employeeNumber(p.employeeNumber())
                        .systemKey(p.systemKey())
                        .systemCode(p.systemCode())
                        .role(p.role())
                        .jan(p.jan()).feb(p.feb()).mar(p.mar()).apr(p.apr())
                        .may(p.may()).jun(p.jun()).jul(p.jul()).aug(p.aug())
                        .sep(p.sep()).oct(p.oct()).nov(p.nov()).dec(p.dec())
                        .projectKey(p.projectKey().isEmpty() ? null : p.projectKey())
                        .projectName(p.projectName().isEmpty() ? null : p.projectName())
                        .importedAt(now)
                        .build()).toList();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }

        transactionTemplate.executeWithoutResult(status -> {
            repository.deleteAllInBatch();
            for (int b = 0; b < rows.size(); b += BATCH_SIZE) {
                repository.saveAll(rows.subList(b, Math.min(b + BATCH_SIZE, rows.size())));
                repository.flush();
            }
        });

        auditService.log("IMPORT", "HR_SYSTEMS", null,
                "rows=" + rows.size() + ", errors=" + errorRows.size());
        return new HrImportResponse(rows.size(), errorRows.size(), errorRows);
    }

    // ------------------------------------------------------------------
    // Excel parsing / validation
    // ------------------------------------------------------------------

    private record ParsedHrRow(String knoxId, String userName, String employed,
                               String regularPartner, String company, String part,
                               String employeeNumber, String systemKey, String systemCode,
                               String role, BigDecimal jan, BigDecimal feb, BigDecimal mar,
                               BigDecimal apr, BigDecimal may, BigDecimal jun, BigDecimal jul,
                               BigDecimal aug, BigDecimal sep, BigDecimal oct, BigDecimal nov,
                               BigDecimal dec, String projectKey, String projectName) {
    }

    private record ParsedHr(List<ParsedHrRow> rows, List<SystemImportRowResult> errors) {
    }

    private Sheet findSheet(Workbook workbook, String name) {
        String wanted = name.replaceAll("\\s+", "").toLowerCase();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String actual = workbook.getSheetName(i).replaceAll("\\s+", "").toLowerCase();
            if (actual.equals(wanted)) {
                return workbook.getSheetAt(i);
            }
        }
        throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID,
                "Missing sheet '" + name + "'. The file must contain the sheet '" + name + "'.");
    }

    private ParsedHr parseSheet(Sheet sheet) {
        HeaderMap header = headerIndex(sheet, HR_HEADERS, HR_HEADER_DISPLAY);
        Map<String, Integer> colIndex = header.colIndex();
        int projectKeyCol = colIndex.getOrDefault("projectkey", -1);
        int projectNameCol = colIndex.getOrDefault("projectname", -1);

        List<ParsedHrRow> rows = new ArrayList<>();
        List<SystemImportRowResult> errors = new ArrayList<>();
        for (int i = header.rowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row, colIndex.get("knoxid"), colIndex.get("systemkey"))) {
                continue;
            }
            int rowNumber = i + 1;
            String knoxId = cellString(row.getCell(colIndex.get("knoxid"))).trim();
            String userName = cellString(row.getCell(colIndex.get("username"))).trim();
            String employed = cellString(row.getCell(colIndex.get("employedy/n"))).trim();
            String regularPartner = cellString(row.getCell(colIndex.get("regular/partner"))).trim();
            String company = cellString(row.getCell(colIndex.get("company"))).trim();
            String part = cellString(row.getCell(colIndex.get("group"))).trim();
            String employeeNumber = cellString(row.getCell(colIndex.get("employeenumber"))).trim();
            String systemKey = cellString(row.getCell(colIndex.get("systemkey"))).trim();
            String systemCode = cellString(row.getCell(colIndex.get("systemcode"))).trim();
            String role = cellString(row.getCell(colIndex.get("role"))).trim();
            String projectKey = projectKeyCol >= 0 ? cellString(row.getCell(projectKeyCol)).trim() : "";
            String projectName = projectNameCol >= 0 ? cellString(row.getCell(projectNameCol)).trim() : "";

            List<String> rowErrors = new ArrayList<>();
            if (knoxId.isBlank()) rowErrors.add("Knox ID cannot be empty.");
            else if (knoxId.length() > 100) rowErrors.add("Knox ID is at most 100 characters.");
            if (userName.isBlank()) rowErrors.add("User Name cannot be empty.");
            else if (userName.length() > 255) rowErrors.add("User Name is at most 255 characters.");
            if (systemKey.isBlank()) rowErrors.add("System Key cannot be empty.");
            else if (systemKey.length() > 100) rowErrors.add("System Key is at most 100 characters.");
            if (systemCode.length() > 100) rowErrors.add("System Code is at most 100 characters.");
            if (role.length() > 100) rowErrors.add("Role is at most 100 characters.");

            BigDecimal[] months = new BigDecimal[12];
            for (int m = 0; m < 12; m++) {
                String monthName = HR_HEADERS.get(10 + m);
                BigDecimal v = cellDecimal(row.getCell(colIndex.get(monthName)));
                if (v != null && v.signum() < 0) {
                    rowErrors.add(HR_HEADER_DISPLAY.get(monthName) + " must not be negative.");
                    v = null;
                }
                months[m] = v;
            }

            if (rowErrors.isEmpty()) {
                rows.add(new ParsedHrRow(knoxId, userName, employed, regularPartner, company,
                        part, employeeNumber, systemKey, systemCode, role,
                        months[0], months[1], months[2], months[3], months[4], months[5],
                        months[6], months[7], months[8], months[9], months[10], months[11],
                        projectKey, projectName));
            } else {
                errors.add(SystemImportRowResult.error(rowNumber, SHEET_HR, systemKey, rowErrors));
            }
        }
        return new ParsedHr(rows, errors);
    }

    /**
     * Finds the column-names row (scans the first 10 rows — the sheet has a
     * group row above the column names) and maps normalized headers to column
     * indexes. Fails when any required column is missing.
     */
    private HeaderMap headerIndex(Sheet sheet, List<String> required,
                                  Map<String, String> display) {
        int lastRow = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 9);
        for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Integer> colIndex = new HashMap<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                colIndex.putIfAbsent(normalizeHeader(cellString(row.getCell(c))), c);
            }
            if (required.stream().allMatch(colIndex::containsKey)) {
                return new HeaderMap(r, colIndex);
            }
        }
        List<String> missing = new ArrayList<>();
        for (String requiredHeader : required) {
            missing.add(display.getOrDefault(requiredHeader, requiredHeader));
        }
        throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID,
                "Header row not found. Required columns: " + String.join(", ", missing)
                        + ". Please use the provided template.");
    }

    private record HeaderMap(int rowIndex, Map<String, Integer> colIndex) {
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String normalizeHeader(String h) {
        return h == null ? "" : h.replaceAll("\\s+", "").toLowerCase();
    }

    private boolean isRowEmpty(Row row, int... cols) {
        for (int c : cols) {
            if (!cellString(row.getCell(c)).isBlank()) return false;
        }
        return true;
    }

    private String cellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> "";
            default -> "";
        };
    }

    /** Reads a numeric cell as BigDecimal (monthly input can be fractional, e.g. 0.5). */
    private BigDecimal cellDecimal(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                String s = cell.getStringCellValue().trim();
                if (s.isEmpty()) yield null;
                try {
                    yield new BigDecimal(s);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

package com.skillmanagement.org.service.impl;

import com.skillmanagement.org.dto.OrgImportResponse;
import com.skillmanagement.org.dto.OrgImportRowResult;
import com.skillmanagement.org.dto.OrgListItem;
import com.skillmanagement.org.dto.OrgSearchRequest;
import com.skillmanagement.org.dto.OrgSearchResult;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.model.UserHistory;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.user.repository.UserHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.service.NascFileDetector;
import com.skillmanagement.org.service.OrganizationService;
import com.skillmanagement.skill.service.ExcelExportStyle;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Organization Management implementation.
 * Import is a SCOPED REPLACE: only the org rows whose (knox_id,
 * employee_number) appear in the file are deleted and re-inserted
 * (batched); all other rows are kept. DEACTIVE: a matched user with no
 * org row Employed=Y is deactivated (no auto-reactivate).
 */
@Service
public class OrganizationServiceImpl implements OrganizationService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    /** Rows committed per batch transaction. */
    private static final int BATCH_SIZE = 500;
    private static final Set<String> EMPLOYED_VALUES = Set.of("Y", "N");
    private static final Set<String> REGULAR_PARTNER_VALUES = Set.of("Regular", "Partner");

    /** Normalized header names (whitespace removed, lower-case). */
    private static final List<String> REQUIRED_HEADERS = List.of(
            "knoxid", "username", "employedy/n", "regular/partner",
            "company", "part", "employeenumber", "group");

    /** Display names for error messages. */
    private static final Map<String, String> HEADER_DISPLAY = Map.ofEntries(
            Map.entry("knoxid", "Knox ID"),
            Map.entry("username", "User Name"),
            Map.entry("employedy/n", "Employed Y/N"),
            Map.entry("regular/partner", "Regular/Partner"),
            Map.entry("company", "Company"),
            Map.entry("part", "Part"),
            Map.entry("employeenumber", "Employee Number"),
            Map.entry("group", "Group"));

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final TransactionTemplate transactionTemplate;

    public OrganizationServiceImpl(OrganizationRepository organizationRepository,
                                   UserRepository userRepository,
                                   UserHistoryRepository userHistoryRepository,
                                   PlatformTransactionManager transactionManager) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.userHistoryRepository = userHistoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public OrgSearchResult search(OrgSearchRequest request) {
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : request.pageSize();

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "importedAt"));
        Specification<OrganizationMaster> spec = buildSpecification(request);
        Page<OrganizationMaster> result = organizationRepository.findAll(spec, pageable);

        List<OrgListItem> items = result.getContent().stream().map(this::toListItem).toList();
        return new OrgSearchResult(result.getTotalElements(), page, pageSize, items);
    }

    private Specification<OrganizationMaster> buildSpecification(OrgSearchRequest r) {
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
            if (notBlank(r.company())) {
                predicates.add(cb.like(cb.lower(root.get("company")),
                        "%" + r.company().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.part())) {
                predicates.add(cb.like(cb.lower(root.get("part")),
                        "%" + r.part().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.group())) {
                predicates.add(cb.like(cb.lower(root.get("groupName")),
                        "%" + r.group().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.employed())) {
                predicates.add(cb.equal(root.get("employed"), r.employed().trim().toUpperCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** matchedUserEmail: user whose Knox ID matches (case-insensitive). id = composite PK joined. */
    private OrgListItem toListItem(OrganizationMaster o) {
        String matchedEmail = userRepository.findByKnoxIdIgnoreCase(o.getKnoxId())
                .stream().map(User::getEmail).findFirst().orElse(null);
        String id = o.getKnoxId() + "|" + o.getEmployeeNumber() + "|" + o.getPart()
                + "|" + o.getGroupName();
        return new OrgListItem(
                id, o.getKnoxId(), o.getUserName(), o.getEmployed(),
                o.getRegularPartner(), o.getCompany(), o.getPart(), o.getGroupName(),
                o.getEmployeeNumber(),
                o.getImportedAt(), matchedEmail);
    }

    // ------------------------------------------------------------------
    // Filter options
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<String> partOptions() {
        return organizationRepository.findDistinctPart();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> groupOptions() {
        return organizationRepository.findDistinctGroupName();
    }

    // ------------------------------------------------------------------
    // Import (batch job: each batch commits in its own transaction)
    // ------------------------------------------------------------------

    @Override
    public OrgImportResponse importExcel(MultipartFile file, String adminLoginId, String clientIp) {
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

        List<OrgImportRowResult> rows;
        Map<Integer, ParsedRow> parsedByRow;
        try (InputStream raw = file.getInputStream()) {
            byte[] head = NascFileDetector.peekHead(raw);
            if (NascFileDetector.isNascMarker(head)) {
                throw new BusinessException(BusinessErrorCode.BULK_FILE_ENCRYPTED,
                        "File is encrypted by Samsung NASC document protection.");
            }
            try (Workbook workbook = WorkbookFactory.create(NascFileDetector.withHead(head, raw))) {
                parsedByRow = parseSheet(workbook);
            }
            rows = validateRows(parsedByRow);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }

        List<OrgImportRowResult> successRows = rows.stream()
                .filter(r -> "SUCCESS".equals(r.status()))
                .toList();

        // Scoped replace: delete only the org rows for the (knox_id,
        // employee_number) pairs present in the file, then re-insert the
        // file's rows in batches. Rows not in the file are kept.
        transactionTemplate.executeWithoutResult(
                status -> deleteRowsForFile(successRows, parsedByRow));

        int matched = 0;
        int deactivated = 0;
        int batchCount = (successRows.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        for (int b = 0; b < batchCount; b++) {
            List<OrgImportRowResult> batch = successRows.subList(
                    b * BATCH_SIZE, Math.min((b + 1) * BATCH_SIZE, successRows.size()));
            transactionTemplate.executeWithoutResult(status -> importBatch(batch, parsedByRow));
        }

        // Safety net: make sure the seeded admin row still exists (it is only
        // removed when the file itself contains knox_id 'admin' with employee
        // number '0000000', in which case the file row replaces it).
        transactionTemplate.executeWithoutResult(status -> ensureAdminRow());

        BatchStats stats = transactionTemplate.execute(
                status -> deactivatePass(successRows, parsedByRow, adminLoginId, clientIp));
        if (stats != null) {
            matched = stats.matched();
            deactivated = stats.deactivated();
        }

        int success = successRows.size();
        return new OrgImportResponse(rows.size(), success, rows.size() - success,
                matched, success - matched, deactivated, rows);
    }

    /** Deletes only the org rows whose (knox_id, employee_number) appear in the file. */
    private void deleteRowsForFile(List<OrgImportRowResult> successRows,
                                   Map<Integer, ParsedRow> parsedByRow) {
        Map<String, Set<String>> empNumbersByKnox = new HashMap<>();
        for (OrgImportRowResult row : successRows) {
            ParsedRow p = parsedByRow.get(row.rowNumber());
            empNumbersByKnox.computeIfAbsent(p.knoxId().toLowerCase(), k -> new HashSet<>())
                    .add(p.employeeNumber());
        }
        List<OrganizationMaster> toDelete = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : empNumbersByKnox.entrySet()) {
            organizationRepository.findByKnoxIdIgnoreCase(entry.getKey()).stream()
                    .filter(o -> entry.getValue().contains(o.getEmployeeNumber()))
                    .forEach(toDelete::add);
        }
        if (!toDelete.isEmpty()) {
            organizationRepository.deleteAll(toDelete);
        }
    }

    /** Safety net: re-inserts the admin org row (same as migration V14) if missing. */
    private void ensureAdminRow() {
        if (organizationRepository.existsByKnoxIdIgnoreCaseAndEmployeeNumber("admin", "0000000")) {
            return;
        }
        OrganizationMaster admin = new OrganizationMaster();
        admin.setKnoxId("admin");
        admin.setUserName("System Admin");
        admin.setEmployed("Y");
        admin.setRegularPartner("Regular");
        admin.setCompany("SDSV");
        admin.setPart("System Admin Part");
        admin.setGroupName("System Admin Group");
        admin.setEmployeeNumber("0000000");
        organizationRepository.save(admin);
    }

    /** One batch transaction: plain insert of the file's rows (their old rows were deleted first). */
    private void importBatch(List<OrgImportRowResult> batch, Map<Integer, ParsedRow> parsedByRow) {
        for (OrgImportRowResult row : batch) {
            ParsedRow p = parsedByRow.get(row.rowNumber());
            OrganizationMaster org = new OrganizationMaster();
            applyParsedFields(org, p);
            organizationRepository.save(org);
        }
    }

    /**
     * DEACTIVE pass, run in one transaction after ALL file rows are inserted:
     * a matched user with no org row Employed=Y in the DB is deactivated
     * (no auto-reactivate). The DB is complete at this point (the file's
     * rows are re-inserted, the admin row is guaranteed to exist), so the
     * check is accurate.
     */
    private BatchStats deactivatePass(List<OrgImportRowResult> successRows,
                                      Map<Integer, ParsedRow> parsedByRow,
                                      String adminLoginId, String clientIp) {
        Set<String> knoxIds = new HashSet<>();
        for (OrgImportRowResult row : successRows) {
            ParsedRow p = parsedByRow.get(row.rowNumber());
            knoxIds.add(p.knoxId().toLowerCase());
        }

        int matched = 0;
        int deactivated = 0;
        for (String knoxId : knoxIds) {
            List<User> users = userRepository.findByKnoxIdIgnoreCase(knoxId);
            if (users.isEmpty()) continue;
            matched += users.size();
            boolean hasEmployedY = organizationRepository.existsByKnoxIdIgnoreCaseAndEmployed(knoxId, "Y");
            if (hasEmployedY) continue;
            for (User u : users) {
                if (!"DEACTIVE".equals(u.getStatus())) {
                    u.setStatus("DEACTIVE");
                    u.setUpdatedBy(adminLoginId);
                    u.setUpdatedIp(clientIp);
                    u.setUpdatedAppId("ORGANIZATION_MANAGEMENT");
                    userRepository.save(u);
                    userHistoryRepository.save(snapshot(u));
                    deactivated++;
                }
            }
        }
        return new BatchStats(matched, deactivated);
    }

    private record BatchStats(int matched, int deactivated) {
    }

    private void applyParsedFields(OrganizationMaster org, ParsedRow p) {
        org.setKnoxId(p.knoxId());
        org.setUserName(p.userName());
        org.setEmployed(p.employed());
        org.setRegularPartner(p.regularPartner());
        org.setCompany(p.company());
        org.setPart(p.part());
        org.setGroupName(p.group());
        org.setEmployeeNumber(p.employeeNumber());
    }

    @Override
    public byte[] downloadTemplate() throws java.io.IOException {
        String[] headers = {
                "Knox ID", "User Name", "Employed Y/N", "Regular/ Partner",
                "Company", "Part", "Employee Number", "Group"
        };
        String[][] samples = {
                {"abc.user1", "John Smith", "Y", "Regular", "SDSV", "Service/EPC IT Part", "1000001", "Cloud Application Group"},
                {"def.user2", "Jane Doe", "N", "Partner", "SDSV", "Service/EPC IT Part", "1000002", "Cloud Application Group"},
        };
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Organization");
            ExcelExportStyle.freezeHeader(sheet);
            CellStyle headerStyle = ExcelExportStyle.headerStyle(workbook);
            CellStyle dataStyle = ExcelExportStyle.dataStyle(workbook);
            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                ExcelExportStyle.setStyled(header, c, headers[c], headerStyle);
            }
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    ExcelExportStyle.setStyled(row, c, samples[r][c], dataStyle);
                }
            }
            ExcelExportStyle.autoSizeColumns(sheet, headers.length - 1, 0);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // Excel parsing / validation
    // ------------------------------------------------------------------

    private record ParsedRow(int rowNumber, String knoxId, String userName, String employed,
                             String regularPartner, String company, String part,
                             String employeeNumber, String group) {
    }

    /** Single pass: parses all data rows (in order) into a rowNumber -> ParsedRow map. */
    private Map<Integer, ParsedRow> parseSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        if (sheet == null) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }

        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }
        Map<String, Integer> colIndex = new HashMap<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            colIndex.putIfAbsent(normalizeHeader(cellString(header.getCell(c))), c);
        }
        List<String> missing = new ArrayList<>();
        for (String required : REQUIRED_HEADERS) {
            if (!colIndex.containsKey(required)) {
                missing.add(HEADER_DISPLAY.getOrDefault(required, required));
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID,
                    "Invalid template. Missing required column(s): " + String.join(", ", missing)
                            + ". Please use the provided template.");
        }

        Map<Integer, ParsedRow> parsed = new HashMap<>();
        for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row, colIndex.get("knoxid"), colIndex.get("username"))) {
                continue;
            }
            parsed.put(i + 1, new ParsedRow(
                    i + 1,
                    cellString(row.getCell(colIndex.get("knoxid"))).trim(),
                    cellString(row.getCell(colIndex.get("username"))).trim(),
                    cellString(row.getCell(colIndex.get("employedy/n"))).trim().toUpperCase(),
                    cellString(row.getCell(colIndex.get("regular/partner"))).trim(),
                    cellString(row.getCell(colIndex.get("company"))).trim(),
                    cellString(row.getCell(colIndex.get("part"))).trim(),
                    cellString(row.getCell(colIndex.get("employeenumber"))).trim(),
                    cellString(row.getCell(colIndex.get("group"))).trim()));
        }
        return parsed;
    }

    /** Validates parsed rows, preserving original row order (rowNumber ascending).
     *  Identity per person is (knox_id, employee_number): at most one row per
     *  person in the file — the import wipes the table and re-inserts. */
    private List<OrgImportRowResult> validateRows(Map<Integer, ParsedRow> parsedByRow) {
        Set<String> seenKeys = new HashSet<>();
        List<OrgImportRowResult> results = new ArrayList<>();
        for (ParsedRow p : parsedByRow.values().stream()
                .sorted(Comparator.comparingInt(ParsedRow::rowNumber))
                .toList()) {
            List<String> errors = new ArrayList<>();
            if (p.knoxId().isBlank()) errors.add("Knox ID cannot be empty.");
            else if (p.knoxId().length() > 100) errors.add("Knox ID is at most 100 characters.");
            if (p.userName().isBlank()) errors.add("User name cannot be empty.");
            else if (p.userName().length() > 255) errors.add("User name is at most 255 characters.");
            if (!EMPLOYED_VALUES.contains(p.employed())) errors.add("Employed must be Y or N.");
            if (!REGULAR_PARTNER_VALUES.contains(p.regularPartner())) {
                errors.add("Regular/Partner must be Regular or Partner.");
            }
            if (p.employeeNumber().isBlank()) errors.add("Employee Number cannot be empty.");
            else if (p.employeeNumber().length() > 50) errors.add("Employee Number is at most 50 characters.");
            if (p.part().isBlank()) errors.add("Part cannot be empty.");
            if (p.group().isBlank()) errors.add("Group cannot be empty.");
            if (errors.isEmpty()) {
                String key = compositeKey(p.knoxId(), p.employeeNumber());
                if (!seenKeys.add(key)) errors.add("Duplicate row: same Knox ID and Employee Number.");
            }
            if (errors.isEmpty()) {
                results.add(OrgImportRowResult.success(p.rowNumber(), p.knoxId(), p.userName()));
            } else {
                results.add(OrgImportRowResult.error(p.rowNumber(), p.knoxId(), p.userName(), errors));
            }
        }
        return results;
    }

    private static String compositeKey(String knoxId, String employeeNumber) {
        return knoxId.toLowerCase() + "|" + employeeNumber;
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

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private UserHistory snapshot(User user) {
        return UserHistory.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .status(user.getStatus())
                .registerId(user.getRegisterId())
                .createdIp(user.getCreatedIp())
                .createdAppId(user.getCreatedAppId())
                .updatedIp(user.getUpdatedIp())
                .updatedAppId(user.getUpdatedAppId())
                .updatedBy(user.getUpdatedBy())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

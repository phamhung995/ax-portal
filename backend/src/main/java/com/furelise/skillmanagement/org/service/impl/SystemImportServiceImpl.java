package com.furelise.skillmanagement.org.service.impl;

import com.furelise.skillmanagement.audit.service.AuditService;
import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.org.dto.SystemImportResponse;
import com.furelise.skillmanagement.org.dto.SystemImportRowResult;
import com.furelise.skillmanagement.org.dto.SystemListItem;
import com.furelise.skillmanagement.org.dto.SystemSearchRequest;
import com.furelise.skillmanagement.org.dto.SystemSearchResult;
import com.furelise.skillmanagement.org.model.SubSystemMaster;
import com.furelise.skillmanagement.org.model.SystemMaster;
import com.furelise.skillmanagement.org.repository.SubSystemMasterRepository;
import com.furelise.skillmanagement.org.repository.SystemMasterRepository;
import com.furelise.skillmanagement.org.service.SystemService;
import com.furelise.skillmanagement.user.service.NascFileDetector;
import jakarta.persistence.criteria.Predicate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * System / Sub System master data implementation.
 * Import is a FULL REPLACE: both tables are wiped and the file's rows are
 * re-inserted (batched). Sub systems referencing a missing system are
 * reported as error rows (valid rows are still imported).
 */
@Service
public class SystemImportServiceImpl implements SystemService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    /** Rows flushed per batch (memory bound) inside the single import transaction. */
    private static final int BATCH_SIZE = 500;

    private static final String SHEET_SYSTEMS = "Systems List";
    private static final String SHEET_SUB_SYSTEMS = "Sub Systems List";

    /** Normalized header names (whitespace removed, lower-case). */
    private static final List<String> SYSTEMS_HEADERS = List.of(
            "systemkey", "systemname", "currentphase",
            "clientofsds(system)", "gdcgroup", "gdcpart");
    private static final Map<String, String> SYSTEMS_HEADER_DISPLAY = Map.of(
            "systemkey", "System Key",
            "systemname", "System Name",
            "currentphase", "Current Phase",
            "clientofsds(system)", "Client of SDS(System)",
            "gdcgroup", "GDC Group",
            "gdcpart", "GDC Part");

    private static final List<String> SUB_SYSTEMS_HEADERS = List.of(
            "systemkey", "subsystemcode", "subsystemname");
    private static final Map<String, String> SUB_SYSTEMS_HEADER_DISPLAY = Map.of(
            "systemkey", "System Key",
            "subsystemcode", "Sub System Code",
            "subsystemname", "Sub System Name");

    private final SystemMasterRepository systemRepository;
    private final SubSystemMasterRepository subSystemRepository;
    private final TransactionTemplate transactionTemplate;
    private final AuditService auditService;

    public SystemImportServiceImpl(SystemMasterRepository systemRepository,
                                   SubSystemMasterRepository subSystemRepository,
                                   PlatformTransactionManager transactionManager,
                                   AuditService auditService) {
        this.systemRepository = systemRepository;
        this.subSystemRepository = subSystemRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SystemSearchResult search(SystemSearchRequest request) {
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : request.pageSize();

        // The grid shows one row per sub system (plus one row per system that
        // has no sub systems), so pagination must be done on the FLAT row
        // list, not on the systems page. Build the full flat list for the
        // matching systems, then slice the requested page out of it.
        List<SystemMaster> systems = systemRepository.findAll(buildSpecification(request),
                Sort.by(Sort.Direction.ASC, "systemKey"));

        List<String> keys = systems.stream().map(SystemMaster::getSystemKey).toList();
        Map<String, List<SubSystemMaster>> subsByKey = new HashMap<>();
        if (!keys.isEmpty()) {
            for (SubSystemMaster s : subSystemRepository.findBySystemKeyIn(keys)) {
                subsByKey.computeIfAbsent(s.getSystemKey(), k -> new ArrayList<>()).add(s);
            }
        }

        List<SystemListItem> allRows = new ArrayList<>();
        for (SystemMaster m : systems) {
            List<SubSystemMaster> subs = subsByKey.getOrDefault(m.getSystemKey(), List.of());
            if (subs.isEmpty()) {
                // System without sub systems: one row with blank sub columns.
                allRows.add(new SystemListItem(null, null, m.getSystemKey(), m.getSystemName(),
                        m.getCurrentPhase(), m.getClientOfSds(), m.getGdcGroup(), m.getGdcPart(),
                        m.getGdcTpmMain(), m.getProjectKey(), m.getProjectName(), null));
            } else {
                for (SubSystemMaster s : subs) {
                    allRows.add(new SystemListItem(s.getSubSystemCode(), s.getSubSystemName(),
                            m.getSystemKey(), m.getSystemName(),
                            m.getCurrentPhase(), m.getClientOfSds(), m.getGdcGroup(), m.getGdcPart(),
                            m.getGdcTpmMain(), m.getProjectKey(), m.getProjectName(), s.getProjectKey()));
                }
            }
        }

        long total = allRows.size();
        int from = Math.min((page - 1) * pageSize, (int) total);
        int to = Math.min(from + pageSize, (int) total);
        return new SystemSearchResult(total, page, pageSize, allRows.subList(from, to));
    }

    private Specification<SystemMaster> buildSpecification(SystemSearchRequest r) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (notBlank(r.systemKey())) {
                predicates.add(cb.like(cb.lower(root.get("systemKey")),
                        "%" + r.systemKey().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.systemName())) {
                predicates.add(cb.like(cb.lower(root.get("systemName")),
                        "%" + r.systemName().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.part())) {
                predicates.add(cb.like(cb.lower(root.get("gdcPart")),
                        "%" + r.part().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.phase())) {
                predicates.add(cb.equal(cb.lower(root.get("currentPhase")),
                        r.phase().trim().toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> partOptions() {
        return systemRepository.findDistinctGdcPart();
    }

    // ------------------------------------------------------------------
    // Import (full replace, batched transactions)
    // ------------------------------------------------------------------

    @Override
    public SystemImportResponse importExcel(MultipartFile file, String adminLoginId, String clientIp) {
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
     * both tables in ONE transaction (readers keep seeing the old data until
     * commit; a failure rolls back to the old data). Shared by the direct
     * import endpoint and the scheduled / "Import Now" job runner.
     */
    public SystemImportResponse doImport(InputStream raw, String adminLoginId) {
        List<SystemMaster> systems;
        List<SubSystemMaster> subSystems;
        List<SystemImportRowResult> errorRows;
        try {
            byte[] head = NascFileDetector.peekHead(raw);
            if (NascFileDetector.isNascMarker(head)) {
                throw new BusinessException(BusinessErrorCode.BULK_FILE_ENCRYPTED,
                        "File is encrypted by Samsung NASC document protection. "
                                + "Open the file in Excel, use Save As to a new file, and upload that one.");
            }
            try (Workbook workbook = WorkbookFactory.create(NascFileDetector.withHead(head, raw))) {
                Sheet systemsSheet = findSheet(workbook, SHEET_SYSTEMS);
                Sheet subSystemsSheet = findSheet(workbook, SHEET_SUB_SYSTEMS);
                ParsedSystems parsedSystems = parseSystemsSheet(systemsSheet);
                ParsedSubSystems parsedSubs = parseSubSystemsSheet(subSystemsSheet, parsedSystems.validKeys());

                errorRows = new ArrayList<>(parsedSystems.errors());
                errorRows.addAll(parsedSubs.errors());
                errorRows.sort(Comparator.comparingInt(SystemImportRowResult::rowNumber));

                Instant now = Instant.now();
                systems = parsedSystems.systems().stream()
                        .map(p -> SystemMaster.builder()
                                .systemKey(p.key())
                                .systemName(p.name())
                                .currentPhase(p.phase())
                                .clientOfSds(p.client())
                                .gdcGroup(p.group())
                                .gdcPart(p.part())
                                .gdcTpmMain(p.tpmMain().isEmpty() ? null : p.tpmMain())
                                .projectKey(p.projectKey().isEmpty() ? null : p.projectKey())
                                .projectName(p.projectName().isEmpty() ? null : p.projectName())
                                .importedAt(now)
                                .build())
                        .toList();
                subSystems = parsedSubs.subs().stream()
                        .map(p -> SubSystemMaster.builder()
                                .systemKey(p.key())
                                .subSystemCode(p.code())
                                .subSystemName(p.name())
                                .projectKey(p.projectKey().isEmpty() ? null : p.projectKey())
                                .importedAt(now)
                                .build())
                        .toList();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }

        // Full replace in ONE transaction: readers keep seeing the old data
        // until commit (MVCC); a failure anywhere rolls back to the old data.
        // Batching only flushes the persistence context to bound memory.
        transactionTemplate.executeWithoutResult(status -> {
            subSystemRepository.deleteAllInBatch();
            systemRepository.deleteAllInBatch();
            for (int b = 0; b < systems.size(); b += BATCH_SIZE) {
                systemRepository.saveAll(systems.subList(b, Math.min(b + BATCH_SIZE, systems.size())));
                systemRepository.flush();
            }
            for (int b = 0; b < subSystems.size(); b += BATCH_SIZE) {
                subSystemRepository.saveAll(subSystems.subList(b, Math.min(b + BATCH_SIZE, subSystems.size())));
                subSystemRepository.flush();
            }
        });

        auditService.log("IMPORT", "SYSTEMS", null,
                "systems=" + systems.size() + ", subSystems=" + subSystems.size()
                        + ", errors=" + errorRows.size());
        return new SystemImportResponse(systems.size(), subSystems.size(),
                errorRows.size(), errorRows);
    }

    // ------------------------------------------------------------------
    // Excel parsing / validation
    // ------------------------------------------------------------------

    private record ParsedSystem(String key, String name, String phase,
                                String client, String group, String part,
                                String tpmMain, String projectKey, String projectName) {
    }

    private record ParsedSystems(List<ParsedSystem> systems, List<SystemImportRowResult> errors) {
        Set<String> validKeys() {
            return systems.stream().map(p -> p.key().toLowerCase())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private record ParsedSub(String key, String code, String name, String projectKey) {
    }

    private record ParsedSubSystems(List<ParsedSub> subs, List<SystemImportRowResult> errors) {
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
                "Missing sheet '" + name + "'. The file must contain the sheets '"
                        + SHEET_SYSTEMS + "' and '" + SHEET_SUB_SYSTEMS + "'.");
    }

    private ParsedSystems parseSystemsSheet(Sheet sheet) {
        HeaderMap header = headerIndex(sheet, SYSTEMS_HEADERS, SYSTEMS_HEADER_DISPLAY);
        Map<String, Integer> colIndex = header.colIndex();
        int projectKeyCol = colIndex.getOrDefault("projectkey", -1);
        int projectNameCol = colIndex.getOrDefault("projectname", -1);
        int tpmMainCol = colIndex.getOrDefault("gdctpm(main)", -1);

        List<ParsedSystem> systems = new ArrayList<>();
        List<SystemImportRowResult> errors = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (int i = header.rowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row, colIndex.get("systemkey"))) {
                continue;
            }
            int rowNumber = i + 1;
            String key = cellString(row.getCell(colIndex.get("systemkey"))).trim();
            String name = cellString(row.getCell(colIndex.get("systemname"))).trim();
            String phase = cellString(row.getCell(colIndex.get("currentphase"))).trim();
            String client = cellString(row.getCell(colIndex.get("clientofsds(system)"))).trim();
            String group = cellString(row.getCell(colIndex.get("gdcgroup"))).trim();
            String part = cellString(row.getCell(colIndex.get("gdcpart"))).trim();
            String projectKey = projectKeyCol >= 0 ? cellString(row.getCell(projectKeyCol)).trim() : "";
            String projectName = projectNameCol >= 0 ? cellString(row.getCell(projectNameCol)).trim() : "";
            String tpmMain = tpmMainCol >= 0 ? cellString(row.getCell(tpmMainCol)).trim() : "";

            List<String> rowErrors = new ArrayList<>();
            if (key.isBlank()) rowErrors.add("System Key cannot be empty.");
            else if (key.length() > 100) rowErrors.add("System Key is at most 100 characters.");
            if (name.isBlank()) rowErrors.add("System Name cannot be empty.");
            else if (name.length() > 255) rowErrors.add("System Name is at most 255 characters.");
            if (rowErrors.isEmpty() && !seenKeys.add(key.toLowerCase())) {
                rowErrors.add("Duplicate System Key in the file.");
            }
            if (rowErrors.isEmpty()) {
                systems.add(new ParsedSystem(key, name, phase, client, group, part,
                        tpmMain, projectKey, projectName));
            } else {
                errors.add(SystemImportRowResult.error(rowNumber, SHEET_SYSTEMS, key, rowErrors));
            }
        }
        return new ParsedSystems(systems, errors);
    }

    private ParsedSubSystems parseSubSystemsSheet(Sheet sheet, Set<String> validKeys) {
        HeaderMap header = headerIndex(sheet, SUB_SYSTEMS_HEADERS, SUB_SYSTEMS_HEADER_DISPLAY);
        Map<String, Integer> colIndex = header.colIndex();
        int projectKeyCol = colIndex.getOrDefault("projectkey", -1);

        List<ParsedSub> subs = new ArrayList<>();
        List<SystemImportRowResult> errors = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (int i = header.rowIndex() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row, colIndex.get("systemkey"), colIndex.get("subsystemcode"))) {
                continue;
            }
            int rowNumber = i + 1;
            String key = cellString(row.getCell(colIndex.get("systemkey"))).trim();
            String code = cellString(row.getCell(colIndex.get("subsystemcode"))).trim();
            String name = cellString(row.getCell(colIndex.get("subsystemname"))).trim();
            String projectKey = projectKeyCol >= 0 ? cellString(row.getCell(projectKeyCol)).trim() : "";

            // System-level placeholder rows (both sub columns blank) are
            // skipped — the system itself shows in the grid with blank sub
            // columns. Rows with only a name (no code) are imported with a
            // null code.
            if (code.isBlank() && name.isBlank()) {
                continue;
            }

            List<String> rowErrors = new ArrayList<>();
            if (key.isBlank()) rowErrors.add("System Key cannot be empty.");
            else if (!validKeys.contains(key.toLowerCase())) {
                rowErrors.add("System Key not found in Systems List.");
            }
            if (!code.isBlank() && code.length() > 100) rowErrors.add("Sub System Code is at most 100 characters.");
            if (name.length() > 255) {
                rowErrors.add("Sub System Name is at most 255 characters.");
            }
            if (rowErrors.isEmpty() && !code.isBlank() && !seenKeys.add(key.toLowerCase() + "|" + code.toLowerCase())) {
                rowErrors.add("Duplicate row: same System Key and Sub System Code.");
            }
            if (rowErrors.isEmpty()) {
                // Source data sometimes lacks a sub system name — fall back
                // to the code so the row is still importable.
                subs.add(new ParsedSub(key, code.isBlank() ? null : code,
                        name.isBlank() ? code : name, projectKey));
            } else {
                errors.add(SystemImportRowResult.error(rowNumber, SHEET_SUB_SYSTEMS, key, rowErrors));
            }
        }
        return new ParsedSubSystems(subs, errors);
    }

    /**
     * Finds the header row (scans the first 10 rows — the Systems sheet has a
     * title row above the real header) and maps normalized headers to column
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

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

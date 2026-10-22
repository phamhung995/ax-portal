package com.skillmanagement.user.service.impl;

import com.skillmanagement.user.dto.*;
import com.skillmanagement.auth.dto.*;
import com.skillmanagement.skill.dto.*;
import com.skillmanagement.auth.dto.OrgRef;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.exception.ResourceNotFoundException;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.model.Role;
import com.skillmanagement.user.dto.*;
import com.skillmanagement.user.model.User;
import com.skillmanagement.user.model.UserHistory;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.user.repository.UserHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.service.NascFileDetector;
import com.skillmanagement.user.service.AdminUserService;
import com.skillmanagement.skill.service.ExcelExportStyle;
import com.skillmanagement.auth.service.PasswordPolicy;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User Management implementation (SRS §4-§9).
 * Admin-only; every mutation writes a USER_HISTORY snapshot.
 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final int MAX_ROWS = 500;
    private static final Set<String> ALLOWED_ROLES = Set.of("NORMAL_USER", "ADMIN_USER");
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "DEACTIVE");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserHistoryRepository userHistoryRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UserSearchResult search(UserSearchRequest request) {
        int page = request.page() == null || request.page() < 1 ? 1 : request.page();
        int pageSize = request.pageSize() == null || request.pageSize() < 1 ? 20 : request.pageSize();

        Sort.Direction direction = "ASC".equalsIgnoreCase(request.sortDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = "createdDate".equalsIgnoreCase(request.sortBy()) ? "createdAt" : "createdAt";
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(direction, sortBy));

        Specification<User> spec = buildSpecification(request);
        Page<User> result = userRepository.findAll(spec, pageable);

        Map<String, List<OrgRef>> orgsByKnox = orgsForUsers(result.getContent());
        List<UserListItem> items = result.getContent().stream().map(u -> new UserListItem(
                u.getId(), u.getName(), u.getEmail(),
                orgsByKnox.getOrDefault(u.getKnoxId().toLowerCase(Locale.ROOT), List.of()),
                u.getRole() != null ? u.getRole().getName() : null, u.getStatus(), u.getCreatedAt()
        )).toList();

        return new UserSearchResult(result.getTotalElements(), page, pageSize, items);
    }

    /** Org rows (Employed=Y) for a set of users, keyed by lower-case knox_id. One query. */
    private Map<String, List<OrgRef>> orgsForUsers(List<User> users) {
        List<String> knoxIds = users.stream().map(User::getKnoxId).distinct().toList();
        if (knoxIds.isEmpty()) return Map.of();
        return organizationRepository.findAll(
                        OrganizationRepository.knoxIdInIgnoreCase(knoxIds))
                .stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .collect(Collectors.groupingBy(
                        o -> o.getKnoxId().toLowerCase(Locale.ROOT),
                        Collectors.mapping(
                                o -> new OrgRef(o.getPart(), o.getGroupName()),
                                Collectors.toList())));
    }

    private Specification<User> buildSpecification(UserSearchRequest r) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (notBlank(r.userName())) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + r.userName().trim().toLowerCase() + "%"));
            }
            if (notBlank(r.email())) {
                predicates.add(cb.like(cb.lower(root.get("email")),
                        "%" + r.email().trim().toLowerCase() + "%"));
            }
            // Part/Group/Organization filters now match org rows (Employed=Y) in organization_master
            // via the user's knox_id (SRS §3.2).
            if (notBlank(r.part())) {
                predicates.add(root.get("knoxId").in(
                        orgKnoxSubquery(query, cb, "part", r.part().trim().toLowerCase())));
            }
            if (notBlank(r.group())) {
                predicates.add(root.get("knoxId").in(
                        orgKnoxSubquery(query, cb, "groupName", r.group().trim().toLowerCase())));
            }
            if (notBlank(r.organization())) {
                // Organization = combined Part > Group display; match either side (SRS §3.2).
                String org = r.organization().trim().toLowerCase();
                Subquery<String> sq = query.subquery(String.class);
                Root<OrganizationMaster> o = sq.from(OrganizationMaster.class);
                sq.select(o.get("knoxId"))
                        .where(cb.equal(o.get("employed"), "Y"),
                                cb.or(cb.equal(cb.lower(o.get("part")), org),
                                        cb.equal(cb.lower(o.get("groupName")), org)));
                predicates.add(root.get("knoxId").in(sq));
            }
            if (r.roles() != null && !r.roles().isEmpty()) {
                predicates.add(root.join("role").get("name").in(r.roles()));
            }
            if (r.statuses() != null && !r.statuses().isEmpty()) {
                predicates.add(root.get("status").in(r.statuses()));
            }
            if (r.createdDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),
                        r.createdDateFrom().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (r.createdDateTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"),
                        r.createdDateTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Subquery: knox_ids of org rows (Employed=Y) whose given column equals the filter value. */
    private Subquery<String> orgKnoxSubquery(CriteriaQuery<?> query, CriteriaBuilder cb,
                                             String orgColumn, String value) {
        Subquery<String> sq = query.subquery(String.class);
        Root<OrganizationMaster> o = sq.from(OrganizationMaster.class);
        sq.select(o.get("knoxId"))
                .where(cb.equal(o.get("employed"), "Y"),
                        cb.equal(cb.lower(o.get(orgColumn)), value));
        return sq;
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UserDetailResponse getDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        return toDetail(user);
    }

    private UserDetailResponse toDetail(User u) {
        List<OrgRef> orgs = organizationRepository.findByKnoxIdIgnoreCase(u.getKnoxId()).stream()
                .filter(o -> "Y".equals(o.getEmployed()))
                .map(o -> new OrgRef(o.getPart(), o.getGroupName()))
                .toList();
        return new UserDetailResponse(
                u.getId(), u.getName(), u.getEmail(), orgs,
                u.getRole() != null ? u.getRole().getName() : null, u.getStatus(),
                u.getCreatedAt(), u.getUpdatedAt(), u.getVersion()
        );
    }

    // ------------------------------------------------------------------
    // Create (SRS §5)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public UserDetailResponse createUser(CreateUserRequest request, String adminLoginId, String clientIp) {
        String email = request.email().trim();

        if (request.userName() == null || request.userName().isBlank()) {
            throw new BusinessException(BusinessErrorCode.NAME_REQUIRED, "User name cannot be empty.");
        }
        if (request.userName().length() > 100) {
            throw new BusinessException(BusinessErrorCode.NAME_TOO_LONG, "User name is at most 100 characters.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(BusinessErrorCode.INVALID_EMAIL, "Invalid email format.");
        }
        if (email.length() > 254) {
            throw new BusinessException(BusinessErrorCode.EMAIL_TOO_LONG, "Email is at most 254 characters.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(BusinessErrorCode.EMAIL_ALREADY_USED, "Email is already in use.");
        }
        String knoxId = email.substring(0, email.indexOf('@'));
        if (userRepository.existsByKnoxIdIgnoreCase(knoxId)) {
            throw new BusinessException(BusinessErrorCode.KNOX_ID_CONFLICT,
                    "A user with this Knox ID (email local part) already exists.");
        }
        PasswordPolicy.validate(request.password());
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(BusinessErrorCode.PASSWORD_MISMATCH, "Password confirmation mismatch.");
        }

        Role normalRole = roleRepository.findByName("NORMAL_USER")
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.REGISTRATION_FAILED,
                        "NORMAL_USER role is not seeded."));

        User user = User.builder()
                .name(request.userName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(normalRole)
                .status("ACTIVE")
                .registerId(adminLoginId)
                .createdIp(clientIp)
                .createdAppId("USER_MANAGEMENT")
                .build();
        userRepository.save(user);
        userHistoryRepository.save(snapshot(user));
        return toDetail(user);
    }

    // ------------------------------------------------------------------
    // Update (SRS §8)
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public UserDetailResponse updateUser(Long userId, UpdateUserRequest request,
                                         String adminLoginId, String clientIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        boolean isSelf = adminLoginId != null && adminLoginId.equalsIgnoreCase(user.getEmail());

        // Self-protection (SRS §8.4)
        if (isSelf && request.role() != null && !request.role().equals(user.getRole().getName())) {
            throw new BusinessException(BusinessErrorCode.SELF_ROLE_CHANGE_FORBIDDEN,
                    "You cannot change your own role.");
        }
        if (isSelf && request.status() != null && !request.status().equals(user.getStatus())) {
            throw new BusinessException(BusinessErrorCode.SELF_STATUS_CHANGE_FORBIDDEN,
                    "You cannot change your own status.");
        }

        // Optimistic lock (SRS §8.6)
        if (request.expectedVersion() != null && !request.expectedVersion().equals(user.getVersion())) {
            throw new BusinessException(BusinessErrorCode.CONCURRENT_UPDATE_CONFLICT,
                    "This user was modified by another session. Please reload and try again.");
        }

        boolean changed = false;

        if (request.userName() != null && !request.userName().isBlank()
                && !request.userName().trim().equals(user.getName())) {
            if (request.userName().trim().length() > 100) {
                throw new BusinessException(BusinessErrorCode.NAME_TOO_LONG, "User name is at most 100 characters.");
            }
            user.setName(request.userName().trim());
            changed = true;
        }
        if (request.role() != null && !request.role().equals(user.getRole().getName())) {
            if (!ALLOWED_ROLES.contains(request.role())) {
                throw new BusinessException(BusinessErrorCode.INVALID_ROLE, "Invalid role.");
            }
            user.setRole(roleRepository.findByName(request.role())
                    .orElseThrow(() -> new BusinessException(BusinessErrorCode.REGISTRATION_FAILED,
                            "Role not found: " + request.role())));
            changed = true;
        }
        if (request.status() != null && !request.status().equals(user.getStatus())) {
            if (!ALLOWED_STATUSES.contains(request.status())) {
                throw new BusinessException(BusinessErrorCode.INVALID_STATUS, "Invalid status.");
            }
            user.setStatus(request.status());
            changed = true;
        }

        if (changed) {
            user.setUpdatedBy(adminLoginId);
            user.setUpdatedIp(clientIp);
            user.setUpdatedAppId("USER_MANAGEMENT");
            userRepository.save(user);
            userHistoryRepository.save(snapshot(user));
        }
        return toDetail(user);
    }

    // ------------------------------------------------------------------
    // Bulk upload (SRS §6)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BulkUploadResponse validateBulkUpload(MultipartFile file) {
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

        List<BulkRowResult> rows;
        try (InputStream raw = file.getInputStream()) {
            byte[] head = NascFileDetector.peekHead(raw);
            if (NascFileDetector.isNascMarker(head)) {
                throw new BusinessException(BusinessErrorCode.BULK_FILE_ENCRYPTED,
                        "File is encrypted by Samsung NASC document protection.");
            }
            try (Workbook workbook = WorkbookFactory.create(NascFileDetector.withHead(head, raw))) {
                rows = readAndValidateRows(workbook);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }

        int success = (int) rows.stream().filter(r -> "SUCCESS".equals(r.status())).count();
        return new BulkUploadResponse(rows.size(), success, rows.size() - success, rows);
    }

    private List<BulkRowResult> readAndValidateRows(Workbook workbook) {
        Sheet sheet = workbook.getSheetAt(0);
        if (sheet == null) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }

        // Header check: User Name | Email | Password | Confirm Password
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID, "Invalid file format.");
        }
        List<String> headers = new ArrayList<>();
        for (int c = 0; c < header.getLastCellNum(); c++) {
            headers.add(cellString(header.getCell(c)).trim().toLowerCase());
        }
        if (!(headers.contains("user name") && headers.contains("email")
                && headers.contains("password") && headers.contains("confirm password"))) {
            throw new BusinessException(BusinessErrorCode.BULK_FILE_INVALID,
                    "Invalid template. Please use the provided template.");
        }
        int nameCol = headers.indexOf("user name");
        int emailCol = headers.indexOf("email");
        int passCol = headers.indexOf("password");
        int confirmCol = headers.indexOf("confirm password");

        List<BulkRowResult> results = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();

        for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            int rowNumber = i + 1; // 1-based, includes header
            if (row == null || isRowEmpty(row, nameCol, emailCol, passCol, confirmCol)) {
                continue;
            }
            if (results.size() >= MAX_ROWS) {
                throw new BusinessException(BusinessErrorCode.BULK_TOO_MANY_ROWS,
                        "Maximum 500 data rows per file.");
            }

            String userName = cellString(row.getCell(nameCol));
            String email = cellString(row.getCell(emailCol)).trim();
            String password = cellString(row.getCell(passCol));
            String confirm = cellString(row.getCell(confirmCol));

            List<String> errors = new ArrayList<>();
            if (userName.isBlank()) errors.add("User name cannot be empty.");
            else if (userName.length() > 100) errors.add("User name is at most 100 characters.");
            if (email.isBlank()) errors.add("Email cannot be empty.");
            else if (!EMAIL_PATTERN.matcher(email).matches()) errors.add("Invalid email format.");
            else if (email.length() > 254) errors.add("Email is at most 254 characters.");
            else if (!seenEmails.add(email.toLowerCase())) errors.add("Email duplicated in uploaded file.");
            else if (userRepository.existsByEmailIgnoreCase(email)) errors.add("Email already exists.");
            if (password.isBlank()) errors.add("Password cannot be empty.");
            else {
                try {
                    PasswordPolicy.validate(password);
                } catch (BusinessException e) {
                    errors.add(e.getMessage());
                }
            }
            if (!password.equals(confirm)) errors.add("Password confirmation mismatch.");

            if (errors.isEmpty()) {
                results.add(BulkRowResult.success(rowNumber, userName, email, password));
            } else {
                results.add(BulkRowResult.error(rowNumber, userName, email, password, String.join("; ", errors)));
            }
        }
        return results;
    }

    @Override
    @Transactional
    public BulkSaveResult saveBulkUpload(List<BulkRowResult> rows, String adminLoginId, String clientIp) {
        int saved = 0;
        int skipped = 0;
        Role normalRole = roleRepository.findByName("NORMAL_USER")
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.REGISTRATION_FAILED,
                        "NORMAL_USER role is not seeded."));

        for (BulkRowResult row : rows) {
            if (!"SUCCESS".equals(row.status())) {
                skipped++;
                continue;
            }
            // Re-validate server-side (late concurrent duplicate)
            if (userRepository.existsByEmailIgnoreCase(row.email())) {
                skipped++;
                continue;
            }
            User user = User.builder()
                    .name(row.userName().trim())
                    .email(row.email().trim())
                    .password(passwordEncoder.encode(row.password()))
                    .role(normalRole)
                    .status("ACTIVE")
                    .registerId(adminLoginId)
                    .createdIp(clientIp)
                    .createdAppId("USER_MANAGEMENT")
                    .build();
            userRepository.save(user);
            userHistoryRepository.save(snapshot(user));
            saved++;
        }
        return new BulkSaveResult(saved, skipped);
    }

    // ------------------------------------------------------------------
    // Export (SRS §9)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public byte[] exportUsers(UserSearchRequest request) throws IOException {
        Specification<User> spec = buildSpecification(request);
        if (request.ids() != null && !request.ids().isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("id").in(request.ids()));
        }
        List<User> users = userRepository.findAll(spec,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Users");
            ExcelExportStyle.freezeHeader(sheet);
            CellStyle headerStyle = ExcelExportStyle.headerStyle(workbook);
            CellStyle dataStyle = ExcelExportStyle.dataStyle(workbook);
            CellStyle wrapStyle = ExcelExportStyle.wrapStyle(workbook);

            String[] headers = {"User Name", "Knox ID", "Email", "Organization", "Role", "Status", "Created Date"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                ExcelExportStyle.setStyled(headerRow, i, headers[i], headerStyle);
            }

            int r = 1;
            Map<String, List<OrgRef>> orgsByKnox = orgsForUsers(users);
            for (User u : users) {
                Row row = sheet.createRow(r++);
                ExcelExportStyle.setStyled(row, 0, u.getName() == null ? "" : u.getName(), dataStyle);
                ExcelExportStyle.setStyled(row, 1, u.getKnoxId() == null ? "" : u.getKnoxId(), dataStyle);
                ExcelExportStyle.setStyled(row, 2, u.getEmail(), dataStyle);
                ExcelExportStyle.setStyled(row, 3, organization(
                        orgsByKnox.getOrDefault(u.getKnoxId().toLowerCase(Locale.ROOT), List.of())), wrapStyle);
                ExcelExportStyle.setStyled(row, 4, u.getRole() != null ? u.getRole().getName() : "", dataStyle);
                ExcelExportStyle.setStyled(row, 5, u.getStatus(), dataStyle);
                ExcelExportStyle.setStyled(row, 6, u.getCreatedAt() != null
                        ? u.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString() : "", dataStyle);
            }
            ExcelExportStyle.autoSizeColumns(sheet, headers.length - 1, 60);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // Organization options (SRS §3.2)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<String> organizationOptions() {
        Set<String> values = new TreeSet<>();
        for (OrganizationMaster o : organizationRepository.findAll()) {
            if (o.getPart() != null && !o.getPart().isBlank()) {
                values.add(o.getPart());
            }
            if (o.getGroupName() != null && !o.getGroupName().isBlank()) {
                values.add(o.getGroupName());
            }
        }
        return new ArrayList<>(values);
    }

    // ------------------------------------------------------------------
    // Filter dropdown search (e.g. Feedback Requester/Handler)
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<UserListItem> searchUsers(UserSearchRequest request, String role) {
        Pageable pageable = PageRequest.of(0, 200, Sort.by(Sort.Direction.ASC, "name"));
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (notBlank(request.userName())) {
                predicates.add(cb.like(cb.lower(root.get("name")),
                        "%" + request.userName().trim().toLowerCase() + "%"));
            }
            if (notBlank(request.email())) {
                predicates.add(cb.like(cb.lower(root.get("email")),
                        "%" + request.email().trim().toLowerCase() + "%"));
            }
            if (notBlank(role)) {
                predicates.add(cb.equal(root.get("role").get("name"), role.trim()));
            }
            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<User> result = userRepository.findAll(spec, pageable);
        Map<String, List<OrgRef>> orgsByKnox = orgsForUsers(result.getContent());
        return result.getContent().stream().map(u -> new UserListItem(
                u.getId(), u.getName(), u.getEmail(),
                orgsByKnox.getOrDefault(u.getKnoxId().toLowerCase(Locale.ROOT), List.of()),
                u.getRole() != null ? u.getRole().getName() : null, u.getStatus(), u.getCreatedAt()
        )).toList();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** "Part > Group" joined by "; " for the export Organization column. */
    private String organization(List<OrgRef> orgs) {
        return orgs.stream()
                .map(o -> o.part() + " > " + o.group())
                .collect(Collectors.joining("; "));
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

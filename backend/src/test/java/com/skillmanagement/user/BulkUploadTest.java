package com.skillmanagement.user;

import com.skillmanagement.user.dto.BulkRowResult;
import com.skillmanagement.user.dto.BulkSaveRequest;
import com.skillmanagement.user.dto.BulkSaveResult;
import com.skillmanagement.user.dto.BulkUploadResponse;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.model.Role;
import com.skillmanagement.user.dto.UserSearchRequest;
import com.skillmanagement.user.model.User;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.repository.RoleRepository;
import com.skillmanagement.user.repository.UserHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.user.service.impl.AdminUserServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkUploadTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserHistoryRepository userHistoryRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AdminUserServiceImpl adminUserService;

    private byte[] workbookBytes;

    @BeforeEach
    void buildWorkbook() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("User Name");
            header.createCell(1).setCellValue("Email");
            header.createCell(2).setCellValue("Password");
            header.createCell(3).setCellValue("Confirm Password");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Bulk User One");
            r1.createCell(1).setCellValue("bulk.one@example.com");
            r1.createCell(2).setCellValue("BulkPass1!");
            r1.createCell(3).setCellValue("BulkPass1!");

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Bulk User Two");
            r2.createCell(1).setCellValue("bulk.two@example.com");
            r2.createCell(2).setCellValue("BulkPass1!");
            r2.createCell(3).setCellValue("BulkPass1!");

            // Row 3: duplicate email within file
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("Bulk User One Dup");
            r3.createCell(1).setCellValue("bulk.one@example.com");
            r3.createCell(2).setCellValue("BulkPass1!");
            r3.createCell(3).setCellValue("BulkPass1!");

            // Row 4: invalid email + weak password + mismatch
            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("Bulk User Four");
            r4.createCell(1).setCellValue("not-an-email");
            r4.createCell(2).setCellValue("weak");
            r4.createCell(3).setCellValue("different");

            wb.write(out);
            workbookBytes = out.toByteArray();
        }
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "bulk.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);
    }

    @Test
    void validateBulkUpload_detectsDuplicatesAndErrors() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        BulkUploadResponse response = adminUserService.validateBulkUpload(file());

        assertEquals(4, response.totalRows());
        assertEquals(2, response.successCount());
        assertEquals(2, response.errorCount());

        // Row order preserved (1-based including header)
        assertEquals(2, response.rows().get(0).rowNumber());
        assertEquals("SUCCESS", response.rows().get(0).status());
        assertEquals(3, response.rows().get(1).rowNumber());
        assertEquals("SUCCESS", response.rows().get(1).status());
        assertEquals(4, response.rows().get(2).rowNumber());
        assertEquals("ERROR", response.rows().get(2).status());
        assertTrue(response.rows().get(2).errors().contains("Email duplicated in uploaded file."));
        assertEquals(5, response.rows().get(3).rowNumber());
        assertEquals("ERROR", response.rows().get(3).status());
        assertTrue(response.rows().get(3).errors().contains("Invalid email format."));
        assertTrue(response.rows().get(3).errors().contains("Password confirmation mismatch."));
    }

    @Test
    void validateBulkUpload_rejectsWrongExtension() {
        MockMultipartFile csv = new MockMultipartFile("file", "bulk.csv",
                "text/csv", "a,b".getBytes());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.validateBulkUpload(csv));
        assertEquals(BusinessErrorCode.BULK_FILE_INVALID, ex.getCode());
    }

    @Test
    void validateBulkUpload_rejectsInvalidTemplate() throws Exception {
        byte[] bad;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Users");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Mail");
            wb.write(out);
            bad = out.toByteArray();
        }
        MockMultipartFile xlsx = new MockMultipartFile("file", "bad.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bad);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminUserService.validateBulkUpload(xlsx));
        assertEquals(BusinessErrorCode.BULK_FILE_INVALID, ex.getCode());
        assertTrue(ex.getMessage().contains("Invalid template"));
    }

    @Test
    void saveBulkUpload_savesSuccessRowsOnly() {
        when(roleRepository.findByName("NORMAL_USER"))
                .thenReturn(Optional.of(Role.builder().name("NORMAL_USER").build()));
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(100L);
            return u;
        });

        List<BulkRowResult> rows = List.of(
                BulkRowResult.success(2, "Bulk User One", "bulk.one@example.com", "BulkPass1!"),
                BulkRowResult.error(4, "Dup", "bulk.one@example.com", "BulkPass1!", "Email duplicated")
        );

        BulkSaveResult result = adminUserService.saveBulkUpload(
                new BulkSaveRequest(rows).rows(), "admin@example.com", "1.2.3.4");

        assertEquals(1, result.savedCount());
        assertEquals(1, result.skippedCount());
        verify(userRepository, times(1)).save(any(User.class));
        verify(userHistoryRepository, times(1)).save(any());
    }

    @Test
    void saveBulkUpload_skipsLateConcurrentDuplicate() {
        when(roleRepository.findByName("NORMAL_USER"))
                .thenReturn(Optional.of(Role.builder().name("NORMAL_USER").build()));
        when(userRepository.existsByEmailIgnoreCase("bulk.one@example.com")).thenReturn(true);

        List<BulkRowResult> rows = List.of(
                BulkRowResult.success(2, "Bulk User One", "bulk.one@example.com", "BulkPass1!"));

        BulkSaveResult result = adminUserService.saveBulkUpload(
                new BulkSaveRequest(rows).rows(), "admin@example.com", "1.2.3.4");

        assertEquals(0, result.savedCount());
        assertEquals(1, result.skippedCount());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void exportUsers_generatesXlsxWithExpectedColumns() throws Exception {
        User u = User.builder()
                .id(1L).name("John").email("john@example.com").knoxId("john")
                .role(Role.builder().name("NORMAL_USER").build())
                .status("ACTIVE")
                .createdAt(java.time.Instant.parse("2026-09-01T00:00:00Z"))
                .build();
        when(userRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(u));
        OrganizationMaster org = OrganizationMaster.builder()
                .knoxId("john").userName("John").employed("Y").regularPartner("Regular")
                .part("Part A").groupName("Group 1").employeeNumber("123").build();
        when(organizationRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(org));

        byte[] xlsx = adminUserService.exportUsers(
                new UserSearchRequest(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertNotNull(xlsx);
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertEquals("User Name", header.getCell(0).getStringCellValue());
            assertEquals("Knox ID", header.getCell(1).getStringCellValue());
            assertEquals("Email", header.getCell(2).getStringCellValue());
            assertEquals("Organization", header.getCell(3).getStringCellValue());
            Row row = sheet.getRow(1);
            assertEquals("John", row.getCell(0).getStringCellValue());
            assertEquals("john", row.getCell(1).getStringCellValue());
            assertEquals("john@example.com", row.getCell(2).getStringCellValue());
            assertEquals("Part A > Group 1", row.getCell(3).getStringCellValue());
        }
    }
}

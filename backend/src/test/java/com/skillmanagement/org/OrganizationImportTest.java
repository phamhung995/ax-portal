package com.skillmanagement.org;

import com.skillmanagement.org.dto.OrgImportResponse;
import com.skillmanagement.org.dto.OrgSearchRequest;
import com.skillmanagement.org.dto.OrgSearchResult;
import com.skillmanagement.exception.BusinessErrorCode;
import com.skillmanagement.exception.BusinessException;
import com.skillmanagement.org.model.OrganizationMaster;
import com.skillmanagement.user.model.User;
import com.skillmanagement.org.repository.OrganizationRepository;
import com.skillmanagement.user.repository.UserHistoryRepository;
import com.skillmanagement.user.repository.UserRepository;
import com.skillmanagement.org.service.impl.OrganizationServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationImportTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserHistoryRepository userHistoryRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @InjectMocks private OrganizationServiceImpl organizationService;

    private byte[] workbookBytes;

    @BeforeEach
    void buildWorkbook() throws Exception {
        // Row 3: same (knox_id, employee_number) as row 1 -> duplicate error.
        // Row 4: same knox_id but different employee_number -> valid distinct row.
        workbookBytes = buildWorkbook(new String[][]{
                {"cmc.lvcong1", "Le Van Cong", "Y", "Partner", "SDSV",
                        "N-SCM Part", "8791264", "Digital SCM Group"},
                {"lv.quang", "Le Van Quang", "N", "Regular", "SDSV",
                        "Management IT Part", "6976639", "Cloud Application Group"},
                {"CMC.LVCONG1", "Le Van Cong Dup", "Y", "Partner", "SDSV",
                        "N-SCM Part", "8791264", "Digital SCM Group 2"},
                {"cmc.lvcong1", "Le Van Cong 2nd Job", "Y", "Partner", "SDSV",
                        "N-SCM Part", "8791265", "Digital SCM Group"},
        });
    }

    private byte[] buildWorkbook(String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Org");
            Row header = sheet.createRow(0);
            String[] cols = {"Knox ID", "User Name", "Employed Y/N", "Regular/ Partner", "Company",
                    "Part", "Employee Number", "Group"};
            for (int c = 0; c < cols.length; c++) header.createCell(c).setCellValue(cols[c]);
            for (int i = 0; i < rows.length; i++) {
                Row r = sheet.createRow(i + 1);
                for (int c = 0; c < rows[i].length; c++) r.createCell(c).setCellValue(rows[i][c]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "org.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    /** Makes the real TransactionTemplate run the batch callback against the mocked TM. */
    private void stubTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void import_scopedReplace_deletesOnlyFileRows_andDeactiveOnlyWhenNoEmployedY() {
        stubTransaction();
        when(organizationRepository.save(any(OrganizationMaster.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Pre-existing org rows for the file's Knox IDs -> deleteAll removes them.
        when(organizationRepository.findByKnoxIdIgnoreCase("cmc.lvcong1")).thenReturn(List.of(
                OrganizationMaster.builder().knoxId("cmc.lvcong1").employeeNumber("8791264")
                        .part("N-SCM Part").groupName("Digital SCM Group").build(),
                OrganizationMaster.builder().knoxId("cmc.lvcong1").employeeNumber("8791265")
                        .part("N-SCM Part").groupName("Digital SCM Group").build()));
        when(organizationRepository.findByKnoxIdIgnoreCase("lv.quang")).thenReturn(List.of(
                OrganizationMaster.builder().knoxId("lv.quang").employeeNumber("6976639")
                        .part("Management IT Part").groupName("Cloud Application Group").build()));
        // The admin row already exists in the DB -> ensureAdminRow() does not save it.
        when(organizationRepository.existsByKnoxIdIgnoreCaseAndEmployeeNumber("admin", "0000000"))
                .thenReturn(true);

        User cong = User.builder().id(1L).name("Le Van Cong").email("cmc.lvcong1@samsung.com")
                .knoxId("cmc.lvcong1").status("ACTIVE").build();
        User quang = User.builder().id(2L).name("Le Van Quang").email("lv.quang@samsung.com")
                .knoxId("lv.quang").status("ACTIVE").build();
        when(userRepository.findByKnoxIdIgnoreCase("cmc.lvcong1")).thenReturn(List.of(cong));
        when(userRepository.findByKnoxIdIgnoreCase("lv.quang")).thenReturn(List.of(quang));
        // cong has an Employed=Y row -> stays ACTIVE; quang only has N -> DEACTIVE
        when(organizationRepository.existsByKnoxIdIgnoreCaseAndEmployed("cmc.lvcong1", "Y")).thenReturn(true);
        when(organizationRepository.existsByKnoxIdIgnoreCaseAndEmployed("lv.quang", "Y")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrgImportResponse res = organizationService.importExcel(file(workbookBytes), "admin@skillmgmt.local", "127.0.0.1");

        assertEquals(4, res.totalRows());
        assertEquals(3, res.successCount());
        assertEquals(1, res.errorCount());
        assertEquals(2, res.matchedCount());
        assertEquals(1, res.deactivatedCount());

        assertEquals("ACTIVE", cong.getStatus());
        assertEquals("DEACTIVE", quang.getStatus());
        // History snapshot written only for the deactivated user
        verify(userHistoryRepository, times(1)).save(any());
        // Scoped replace: only the rows for the file's (knox_id, employee_number)
        // pairs are deleted (via deleteAll), the whole table is NOT wiped.
        verify(organizationRepository).deleteAll(anyList());
        verify(organizationRepository, never()).deleteAllInBatch();
    }

    @Test
    void import_userWithOnlyNRowsIsDeactivated() throws Exception {
        stubTransaction();
        when(organizationRepository.save(any(OrganizationMaster.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(organizationRepository.existsByKnoxIdIgnoreCaseAndEmployeeNumber("admin", "0000000"))
                .thenReturn(true);

        User cong = User.builder().id(1L).name("Le Van Cong").email("cmc.lvcong1@samsung.com")
                .knoxId("cmc.lvcong1").status("ACTIVE").build();
        when(userRepository.findByKnoxIdIgnoreCase("cmc.lvcong1")).thenReturn(List.of(cong));
        when(organizationRepository.existsByKnoxIdIgnoreCaseAndEmployed(anyString(), eq("Y"))).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // All rows for cong are Employed=N -> DEACTIVE
        byte[] allN = buildWorkbook(new String[][]{
                {"cmc.lvcong1", "Le Van Cong", "N", "Partner", "SDSV",
                        "N-SCM Part", "8791264", "Digital SCM Group"},
                {"cmc.lvcong1", "Le Van Cong", "N", "Partner", "SDSV",
                        "N-SCM Part", "8791265", "Digital SCM Group 2"},
        });
        OrgImportResponse res = organizationService.importExcel(file(allN), "admin@skillmgmt.local", "127.0.0.1");

        assertEquals(2, res.successCount());
        assertEquals(1, res.deactivatedCount());
        assertEquals("DEACTIVE", cong.getStatus());
    }

    @Test
    void import_unknownKnoxId_isStoredButNotMatched() {
        stubTransaction();
        when(organizationRepository.save(any(OrganizationMaster.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByKnoxIdIgnoreCase(anyString())).thenReturn(List.of());
        // The admin row does not exist in the DB -> ensureAdminRow() re-seeds it.
        when(organizationRepository.existsByKnoxIdIgnoreCaseAndEmployeeNumber("admin", "0000000"))
                .thenReturn(false);

        OrgImportResponse res = organizationService.importExcel(file(workbookBytes), "admin@skillmgmt.local", "127.0.0.1");

        assertEquals(3, res.successCount());
        assertEquals(0, res.matchedCount());
        assertEquals(3, res.unmatchedCount());
        verify(userRepository, never()).save(any(User.class));
        verify(userHistoryRepository, never()).save(any());
        // 3 file rows + 1 re-seeded admin row
        verify(organizationRepository, times(4)).save(any(OrganizationMaster.class));
    }

    @Test
    void import_compositeKeyDuplicateInFile_rowError() {
        stubTransaction();
        when(organizationRepository.save(any(OrganizationMaster.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByKnoxIdIgnoreCase(anyString())).thenReturn(List.of());

        OrgImportResponse res = organizationService.importExcel(file(workbookBytes), "admin@skillmgmt.local", "127.0.0.1");

        var dupRow = res.rows().stream().filter(r -> "ERROR".equals(r.status())).findFirst().orElseThrow();
        // rowNumber is the Excel row number: header = 1, data rows start at 2.
        // The duplicate is the 3rd data row -> row 4.
        assertEquals(4, dupRow.rowNumber());
        assertTrue(dupRow.errors().stream().anyMatch(e -> e.contains("Duplicate")));
    }

    @Test
    void import_requiredColumnsBlank_rowError() throws Exception {
        byte[] bad = buildWorkbook(new String[][]{
                {"knox1", "User 1", "Y", "Regular", "SDSV", "", "100", "Group 1"},
                {"knox2", "User 2", "Y", "Regular", "SDSV", "Part 1", "", "Group 1"},
                {"knox3", "User 3", "Y", "Regular", "SDSV", "Part 1", "100", ""},
        });
        OrgImportResponse res = organizationService.importExcel(file(bad), "admin", "127.0.0.1");

        assertEquals(0, res.successCount());
        assertEquals(3, res.errorCount());
        assertTrue(res.rows().get(0).errors().stream().anyMatch(e -> e.contains("Part")));
        assertTrue(res.rows().get(1).errors().stream().anyMatch(e -> e.contains("Employee Number")));
        assertTrue(res.rows().get(2).errors().stream().anyMatch(e -> e.contains("Group")));
    }

    @Test
    void import_rejectsNascEncryptedFile() {
        // NASC DRM encrypted files start with this marker instead of a ZIP "PK" header.
        byte[] encrypted = "<## NASCA DRM FILE - do not open".getBytes();
        MockMultipartFile f = new MockMultipartFile("file", "org.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", encrypted);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> organizationService.importExcel(f, "admin", "127.0.0.1"));
        assertEquals(BusinessErrorCode.BULK_FILE_ENCRYPTED, ex.getCode());
        assertTrue(ex.getMessage().contains("NASC"));
    }

    @Test
    void import_rejectsWrongExtension() {
        MockMultipartFile csv = new MockMultipartFile("file", "org.csv", "text/csv", "x".getBytes());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> organizationService.importExcel(csv, "admin", "127.0.0.1"));
        assertEquals("Only .xlsx and .xls files are supported.", ex.getMessage());
    }

    @Test
    void import_rejectsInvalidTemplate() throws Exception {
        byte[] bad;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Org");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Knox ID");
            header.createCell(1).setCellValue("User Name");
            // missing required columns
            wb.write(out);
            bad = out.toByteArray();
        }
        MockMultipartFile f = new MockMultipartFile("file", "org.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bad);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> organizationService.importExcel(f, "admin", "127.0.0.1"));
        assertTrue(ex.getMessage().startsWith("Invalid template. Missing required column(s):"));
        assertTrue(ex.getMessage().contains("Employed Y/N"));
        assertTrue(ex.getMessage().contains("Employee Number"));
    }

    @Test
    void import_over500Rows_splitsIntoBatches() throws Exception {
        byte[] big;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Org");
            Row header = sheet.createRow(0);
            String[] cols = {"Knox ID", "User Name", "Employed Y/N", "Regular/ Partner", "Company",
                    "Part", "Employee Number", "Group"};
            for (int c = 0; c < cols.length; c++) header.createCell(c).setCellValue(cols[c]);
            for (int i = 0; i < 501; i++) {
                Row r = sheet.createRow(i + 1);
                r.createCell(0).setCellValue("knox" + i);
                r.createCell(1).setCellValue("User " + i);
                r.createCell(2).setCellValue("Y");
                r.createCell(3).setCellValue("Regular");
                r.createCell(4).setCellValue("SDSV");
                r.createCell(5).setCellValue("Part " + i);
                r.createCell(6).setCellValue("100" + i);
                r.createCell(7).setCellValue("Group " + i);
            }
            wb.write(out);
            big = out.toByteArray();
        }
        MockMultipartFile f = new MockMultipartFile("file", "big.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", big);

        stubTransaction();
        when(organizationRepository.save(any(OrganizationMaster.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByKnoxIdIgnoreCase(anyString())).thenReturn(List.of());
        // The admin row does not exist in the DB -> ensureAdminRow() re-seeds it.
        when(organizationRepository.existsByKnoxIdIgnoreCaseAndEmployeeNumber("admin", "0000000"))
                .thenReturn(false);

        OrgImportResponse res = organizationService.importExcel(f, "admin@skillmgmt.local", "127.0.0.1");

        assertEquals(501, res.totalRows());
        assertEquals(501, res.successCount());
        // 5 transactions: scoped delete + 2 import batches (500 + 1)
        // + ensureAdminRow + deactivate pass
        verify(transactionManager, times(5)).getTransaction(any());
        // 501 file rows + 1 re-seeded admin row
        verify(organizationRepository, times(502)).save(any(OrganizationMaster.class));
    }

    @Test
    void import_batchFailure_keepsPreviousBatches() throws Exception {
        byte[] big;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Org");
            Row header = sheet.createRow(0);
            String[] cols = {"Knox ID", "User Name", "Employed Y/N", "Regular/ Partner", "Company",
                    "Part", "Employee Number", "Group"};
            for (int c = 0; c < cols.length; c++) header.createCell(c).setCellValue(cols[c]);
            for (int i = 0; i < 501; i++) {
                Row r = sheet.createRow(i + 1);
                r.createCell(0).setCellValue("knox" + i);
                r.createCell(1).setCellValue("User " + i);
                r.createCell(2).setCellValue("Y");
                r.createCell(3).setCellValue("Regular");
                r.createCell(4).setCellValue("SDSV");
                r.createCell(5).setCellValue("Part " + i);
                r.createCell(6).setCellValue("100" + i);
                r.createCell(7).setCellValue("Group " + i);
            }
            wb.write(out);
            big = out.toByteArray();
        }
        MockMultipartFile f = new MockMultipartFile("file", "big.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", big);

        stubTransaction();
        java.util.concurrent.atomic.AtomicInteger saves = new java.util.concurrent.atomic.AtomicInteger();
        when(organizationRepository.save(any(OrganizationMaster.class)))
                .thenAnswer(inv -> {
                    // saves #1..#500 = batch 1 (500 file rows);
                    // #501 = first row of batch 2 -> failure
                    if (saves.incrementAndGet() > 500) {
                        throw new RuntimeException("DB down");
                    }
                    return inv.getArgument(0);
                });
        // Note: no userRepository stubbing — the deactivate pass never runs
        // because batch 2 fails before it.

        assertThrows(RuntimeException.class,
                () -> organizationService.importExcel(f, "admin@skillmgmt.local", "127.0.0.1"));

        // Batch 1 (500 rows) committed before the failure; batch 2 rolled back.
        verify(transactionManager, times(3)).getTransaction(any());
        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager, times(1)).rollback(any());
        // 500 saves in batch 1 + 1 failed save attempt in batch 2
        // (the admin row already exists -> ensureAdminRow() does not save it)
        verify(organizationRepository, times(501)).save(any(OrganizationMaster.class));
    }

    @Test
    void search_returnsPagedItemsWithMatchedEmail() {
        OrganizationMaster o = OrganizationMaster.builder()
                .knoxId("cmc.lvcong1").userName("Le Van Cong")
                .employed("Y").regularPartner("Partner").company("SDSV")
                .part("N-SCM Part").groupName("Digital SCM Group").employeeNumber("8791264")
                .build();
        Page<OrganizationMaster> page = new PageImpl<>(List.of(o));
        when(organizationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(userRepository.findByKnoxIdIgnoreCase("cmc.lvcong1"))
                .thenReturn(List.of(User.builder().id(1L).email("cmc.lvcong1@samsung.com").build()));

        OrgSearchResult res = organizationService.search(
                new OrgSearchRequest(null, null, null, null, null, null, 1, 20));

        assertEquals(1, res.totalCount());
        assertEquals("cmc.lvcong1@samsung.com", res.items().get(0).matchedUserEmail());
        assertEquals("Digital SCM Group", res.items().get(0).group());
        assertEquals("N-SCM Part", res.items().get(0).part());
        assertNotNull(res.items().get(0).id());
    }
}

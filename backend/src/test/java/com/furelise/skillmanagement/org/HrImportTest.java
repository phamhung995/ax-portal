package com.furelise.skillmanagement.org;

import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.org.dto.HrImportResponse;
import com.furelise.skillmanagement.org.repository.HrSystemAssignmentRepository;
import com.furelise.skillmanagement.org.service.impl.HrSystemServiceImpl;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HrImportTest {

    @Mock private HrSystemAssignmentRepository repository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private com.furelise.skillmanagement.audit.service.AuditService auditService;
    @InjectMocks private HrSystemServiceImpl hrService;

    private byte[] workbookBytes;

    @BeforeEach
    void buildWorkbook() throws Exception {
        workbookBytes = buildWorkbook(
                // knoxId, userName, employed, regularPartner, company, group, empNo,
                // systemKey, systemCode, role, jan..dec
                new String[][]{
                        {"cmc.lvcong1", "Le Van Cong", "Y", "Partner", "SDSV", "N-SCM Part", "8791264",
                                "SYS01", "MSPCMDBCHG-917768", "Developer",
                                "1", "1", "1", "0", "0", "0", "0", "0", "0", "0", "0", "0"},
                        {"nm.dat1", "Nguyen Minh Dat", "Y", "Regular", "SDSV", "Sales IT Part", "6976987",
                                "SYS02", "MSPCMDBCHG-1296814", "Developer",
                                "0.5", "0.5", "0.5", "0.5", "0.5", "0.5", "0.5", "0.5", "0.5", "0.5", "0", "0"},
                        {"", "Missing Knox", "Y", "Regular", "SDSV", "QA Part", "1",
                                "SYS03", "", "Developer",
                                "1", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0"},
                        {"nm.dat1", "Nguyen Minh Dat", "Y", "Regular", "SDSV", "Sales IT Part", "6976987",
                                "SYS02", "MSPCMDBCHG-1296814", "Developer",
                                "1", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0"},
                });
    }

    private byte[] buildWorkbook(String[][] dataRows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Human Resource System List");

            // Row 0: group row (like the real file)
            Row groupRow = sheet.createRow(0);
            groupRow.createCell(0).setCellValue("Human Resource Info");
            groupRow.createCell(10).setCellValue("System Info");
            groupRow.createCell(18).setCellValue("Input Info");

            // Row 1: column names
            Row header = sheet.createRow(1);
            String[] headers = {"Knox ID", "User Name", "Employed Y/N", "Country", "Regular/ Partner",
                    "Company", "Group", "Department Locate", "Leader Y/N", "Employee Number",
                    "System Key", "Mgmt. Tool", "System Code", "System Name", "GDC Project Type",
                    "System Type", "Cost Distribution Y/N", "WBS Code (Cost Distribution)",
                    "Current Input Status", "Role", "Level", "Skill", "Start Date", "End Date",
                    "Total M/M", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep",
                    "Oct", "Nov", "Dec", "Project Key"};
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]);
            }

            // Data rows start at row 2, same column layout as the headers.
            for (int r = 0; r < dataRows.length; r++) {
                Row row = sheet.createRow(2 + r);
                String[] d = dataRows[r];
                row.createCell(0).setCellValue(d[0]);   // Knox ID
                row.createCell(1).setCellValue(d[1]);   // User Name
                row.createCell(2).setCellValue(d[2]);   // Employed Y/N
                row.createCell(4).setCellValue(d[3]);   // Regular/ Partner
                row.createCell(5).setCellValue(d[4]);   // Company
                row.createCell(6).setCellValue(d[5]);   // Group
                row.createCell(9).setCellValue(d[6]);   // Employee Number
                row.createCell(10).setCellValue(d[7]);  // System Key
                row.createCell(12).setCellValue(d[8]);  // System Code
                row.createCell(19).setCellValue(d[9]);  // Role
                for (int m = 0; m < 12; m++) {
                    row.createCell(25 + m).setCellValue(Double.parseDouble(d[10 + m]));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "human-resource-list_2026.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private void stubTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void import_parsesAllColumns_andReportsErrors() {
        stubTransaction();
        when(repository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        HrImportResponse res = hrService.importExcel(file(workbookBytes), "admin@skillmgmt.local");

        // 3 valid rows (row 3 missing Knox ID is the only error — repeated
        // knox+system rows are allowed).
        assertEquals(3, res.rowsCount());
        assertEquals(1, res.errorCount());
        assertTrue(res.errorRows().stream().anyMatch(r -> "SYS03".equals(r.systemKey())
                && r.errors().stream().anyMatch(e -> e.contains("Knox ID"))));

        // Full replace: table wiped, then batched inserts.
        verify(repository).deleteAllInBatch();
        verify(repository).saveAll(anyList());
    }

    @Test
    void import_missingSheet_throws() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Wrong Name");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Knox ID");
            wb.write(out);
            assertThrows(BusinessException.class,
                    () -> hrService.importExcel(file(out.toByteArray()), "admin"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void import_negativeMonth_rowError() throws Exception {
        stubTransaction();

        byte[] wb = buildWorkbook(new String[][]{
                {"cmc.a", "User A", "Y", "Regular", "SDSV", "Part 1", "1",
                        "SYS01", "CODE1", "Developer",
                        "-1", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0"},
        });

        HrImportResponse res = hrService.importExcel(file(wb), "admin");

        assertEquals(0, res.rowsCount());
        assertEquals(1, res.errorCount());
        assertTrue(res.errorRows().get(0).errors().stream().anyMatch(e -> e.contains("Jan")
                && e.contains("negative")));
    }
}

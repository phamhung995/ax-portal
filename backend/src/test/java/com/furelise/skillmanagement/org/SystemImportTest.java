package com.furelise.skillmanagement.org;

import com.furelise.skillmanagement.exception.BusinessErrorCode;
import com.furelise.skillmanagement.exception.BusinessException;
import com.furelise.skillmanagement.org.dto.SystemImportResponse;
import com.furelise.skillmanagement.org.model.SubSystemMaster;
import com.furelise.skillmanagement.org.model.SystemMaster;
import com.furelise.skillmanagement.org.repository.SubSystemMasterRepository;
import com.furelise.skillmanagement.org.repository.SystemMasterRepository;
import com.furelise.skillmanagement.org.service.impl.SystemImportServiceImpl;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class SystemImportTest {

    @Mock private SystemMasterRepository systemRepository;
    @Mock private SubSystemMasterRepository subSystemRepository;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private com.furelise.skillmanagement.audit.service.AuditService auditService;
    @InjectMocks private SystemImportServiceImpl systemService;

    private byte[] workbookBytes;

    @BeforeEach
    void buildWorkbook() throws Exception {
        workbookBytes = buildWorkbook(
                // Systems List: key, name, phase, client, group, part, tpmMain
                new String[][]{
                        {"SYS01", "System One", "Active", "Client A", "Group X", "Part 1", "TPM-1"},
                        {"SYS02", "System Two", "End", "Client B", "Group X", "Part 1", ""},
                        {"SYS03", "System Three", "Build", "Client C", "Group Y", "Part 2", "TPM-3"},
                },
                // Sub Systems List: key, code, name
                new String[][]{
                        {"SYS01", "SUB01", "Sub One A"},
                        {"SYS01", "SUB02", "Sub One B"},
                        {"SYS02", "SUB03", "Sub Two (system is End — still valid)"},
                        {"SYS99", "SUB04", "Sub Unknown (no such system)"},                // orphan error
                });
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "system-list.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private void stubTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    }

    @Test
    void import_includesEndSystems_andReportsOrphanSubs() {
        stubTransaction();
        when(systemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(subSystemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SystemImportResponse res = systemService.importExcel(file(workbookBytes), "admin@skillmgmt.local", "127.0.0.1");

        // All 3 systems imported (End phase included). Only SUB04 (SYS99 unknown) is an error.
        assertEquals(3, res.systemsCount());
        assertEquals(3, res.subSystemsCount());
        assertEquals(1, res.errorCount());
        assertTrue(res.errorRows().stream().anyMatch(r -> "SYS99".equals(r.systemKey())
                && r.errors().stream().anyMatch(e -> e.contains("not found"))));

        // Full replace: both tables wiped (children first), then batched inserts.
        verify(subSystemRepository).deleteAllInBatch();
        verify(systemRepository).deleteAllInBatch();
        verify(systemRepository).saveAll(anyList());
        verify(subSystemRepository).saveAll(anyList());
    }

    @Test
    void import_duplicateSystemKey_rowError() throws Exception {
        stubTransaction();
        when(systemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(subSystemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        byte[] wb = buildWorkbook(
                new String[][]{
                        {"SYS01", "System One", "Active", "Client A", "Group X", "Part 1", "TPM-1"},
                        {"SYS01", "System One Dup", "Build", "Client A", "Group X", "Part 1", "TPM-1"}, // dup
                },
                new String[][]{
                        {"SYS01", "SUB01", "Sub One A"},
                });

        SystemImportResponse res = systemService.importExcel(file(wb), "admin@skillmgmt.local", "127.0.0.1");

        assertEquals(1, res.systemsCount());
        assertEquals(1, res.errorCount());
        assertTrue(res.errorRows().get(0).errors().stream().anyMatch(e -> e.contains("Duplicate")));
    }

    @Test
    void import_subRowWithoutCode_importedWithNullCode() throws Exception {
        stubTransaction();
        when(systemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(subSystemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        byte[] wb = buildWorkbook(
                new String[][]{
                        {"SYS01", "System One", "Active", "Client A", "Group X", "Part 1", "TPM-1"},
                },
                new String[][]{
                        {"SYS01", "SUB01", "Sub One A"},
                        {"SYS01", "", "Customer System"}, // no code — imported with null code
                        {"SYS01", "SUB02", ""},           // no name — imported with name = code
                });

        SystemImportResponse res = systemService.importExcel(file(wb), "admin@skillmgmt.local", "127.0.0.1");

        assertEquals(1, res.systemsCount());
        assertEquals(3, res.subSystemsCount());
        assertEquals(0, res.errorCount());
    }

    @Test
    void import_parsesGdcTpmMainColumn() throws Exception {
        stubTransaction();
        ArgumentCaptor<List<SystemMaster>> captor = ArgumentCaptor.forClass(List.class);
        when(systemRepository.saveAll(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(subSystemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SystemImportResponse res = systemService.importExcel(file(workbookBytes), "admin@skillmgmt.local", "127.0.0.1");

        assertEquals(3, res.systemsCount());
        List<SystemMaster> saved = captor.getValue();
        SystemMaster sys01 = saved.stream().filter(m -> "SYS01".equals(m.getSystemKey())).findFirst().orElseThrow();
        SystemMaster sys02 = saved.stream().filter(m -> "SYS02".equals(m.getSystemKey())).findFirst().orElseThrow();
        assertEquals("TPM-1", sys01.getGdcTpmMain());
        // Blank cell → null (not an empty string).
        assertNull(sys02.getGdcTpmMain());
    }

    @Test
    void import_gdcTpmMainColumnOptional_fileWithoutItStillImports() throws Exception {
        // The column is optional: a file without "GDC TPM(Main)" still imports.
        stubTransaction();
        when(systemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sysSheet = wb.createSheet("Systems List");
            Row sysHeader = sysSheet.createRow(0);
            String[] sysCols = {"System Key", "System Name", "Current Phase",
                    "Client of SDS(System)", "GDC Group", "GDC Part"};
            for (int c = 0; c < sysCols.length; c++) sysHeader.createCell(c).setCellValue(sysCols[c]);
            Row sysRow = sysSheet.createRow(1);
            String[] sysVals = {"SYS01", "System One", "Active", "Client A", "Group X", "Part 1"};
            for (int c = 0; c < sysVals.length; c++) sysRow.createCell(c).setCellValue(sysVals[c]);
            Sheet subSheet = wb.createSheet("Sub Systems List");
            Row subHeader = subSheet.createRow(0);
            subHeader.createCell(0).setCellValue("System Key");
            subHeader.createCell(1).setCellValue("Sub System Code");
            subHeader.createCell(2).setCellValue("Sub System Name");
            wb.write(out);

            SystemImportResponse res = systemService.importExcel(file(out.toByteArray()), "admin", "127.0.0.1");
            assertEquals(1, res.systemsCount());
            assertEquals(0, res.errorCount());
        }
    }

    @Test
    void import_missingSheet_throws() {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Wrong Name");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("System Key");
            wb.write(out);
            assertThrows(BusinessException.class,
                    () -> systemService.importExcel(file(out.toByteArray()), "admin", "127.0.0.1"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void import_missingRequiredColumn_throws() {
        // Systems sheet lacks "Current Phase" column.
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Systems List");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("System Key");
            header.createCell(1).setCellValue("System Name");
            Sheet sub = wb.createSheet("Sub Systems List");
            Row subHeader = sub.createRow(0);
            subHeader.createCell(0).setCellValue("System Key");
            subHeader.createCell(1).setCellValue("Sub System Code");
            subHeader.createCell(2).setCellValue("Sub System Name");
            wb.write(out);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> systemService.importExcel(file(out.toByteArray()), "admin", "127.0.0.1"));
            assertEquals(BusinessErrorCode.BULK_FILE_INVALID, ex.getCode());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void import_headerOnSecondRow_titleRowAbove() throws Exception {
        stubTransaction();
        when(systemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(subSystemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // Systems sheet: row 1 = title banner, row 2 = real header (like the
        // real system-list export). Sub Systems sheet: header on row 1.
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sysSheet = wb.createSheet("Systems List");
            sysSheet.createRow(0).createCell(0).setCellValue("System Information");
            Row sysHeader = sysSheet.createRow(1);
            String[] sysCols = {"System Key", "System Code", "System Name", "Current Phase",
                    "Client of SDS(System)", "GDC Group", "GDC Part"};
            for (int c = 0; c < sysCols.length; c++) sysHeader.createCell(c).setCellValue(sysCols[c]);
            Row sysRow = sysSheet.createRow(2);
            String[] sysVals = {"SYS01", "SC01", "System One", "Active", "Client A", "Group X", "Part 1"};
            for (int c = 0; c < sysVals.length; c++) sysRow.createCell(c).setCellValue(sysVals[c]);

            Sheet subSheet = wb.createSheet("Sub Systems List");
            Row subHeader = subSheet.createRow(0);
            String[] subCols = {"System Key", "Sub System Code", "Sub System Name"};
            for (int c = 0; c < subCols.length; c++) subHeader.createCell(c).setCellValue(subCols[c]);
            Row subRow = subSheet.createRow(1);
            String[] subVals = {"SYS01", "SUB01", "Sub One A"};
            for (int c = 0; c < subVals.length; c++) subRow.createCell(c).setCellValue(subVals[c]);

            wb.write(out);
            SystemImportResponse res = systemService.importExcel(
                    file(out.toByteArray()), "admin", "127.0.0.1");
            assertEquals(1, res.systemsCount());
            assertEquals(1, res.subSystemsCount());
            assertEquals(0, res.errorCount());
        }
    }

    @Test
    void import_realSystemListFile_parses() throws Exception {
        // Guarded by file existence — runs only on the machine holding the
        // real export (C:\Users\tq.dat1\Downloads\system-list_2026 (8).xlsx).
        // Skipped when the file is NASC-encrypted (server cannot decrypt).
        java.nio.file.Path real = java.nio.file.Paths.get(
                "C:/Users/tq.dat1/Downloads/system-list_2026 (8).xlsx");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(real));
        byte[] bytes = java.nio.file.Files.readAllBytes(real);
        org.junit.jupiter.api.Assumptions.assumeFalse(
                com.furelise.skillmanagement.user.service.NascFileDetector
                        .isNascMarker(com.furelise.skillmanagement.user.service.NascFileDetector
                                .peekHead(new java.io.ByteArrayInputStream(bytes))),
                "real file is NASC-encrypted");
        stubTransaction();
        when(systemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(subSystemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        SystemImportResponse res = systemService.importExcel(file(bytes), "admin", "127.0.0.1");

        System.out.println("[REAL FILE] systems=" + res.systemsCount()
                + " subSystems=" + res.subSystemsCount()
                + " errors=" + res.errorCount());
        assertTrue(res.systemsCount() > 0, "expected at least one system");
        assertTrue(res.subSystemsCount() > 0, "expected at least one sub system");
    }

    private byte[] buildWorkbook(String[][] systemRows, String[][] subRows) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sysSheet = wb.createSheet("Systems List");
            Row sysHeader = sysSheet.createRow(0);
            String[] sysCols = {"System Key", "System Name", "Current Phase",
                    "Client of SDS(System)", "GDC Group", "GDC Part", "GDC TPM(Main)"};
            for (int c = 0; c < sysCols.length; c++) sysHeader.createCell(c).setCellValue(sysCols[c]);
            for (int i = 0; i < systemRows.length; i++) {
                Row r = sysSheet.createRow(i + 1);
                for (int c = 0; c < systemRows[i].length; c++) r.createCell(c).setCellValue(systemRows[i][c]);
            }

            Sheet subSheet = wb.createSheet("Sub Systems List");
            Row subHeader = subSheet.createRow(0);
            String[] subCols = {"System Key", "Sub System Code", "Sub System Name"};
            for (int c = 0; c < subCols.length; c++) subHeader.createCell(c).setCellValue(subCols[c]);
            for (int i = 0; i < subRows.length; i++) {
                Row r = subSheet.createRow(i + 1);
                for (int c = 0; c < subRows[i].length; c++) r.createCell(c).setCellValue(subRows[i][c]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}

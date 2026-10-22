package com.skillmanagement.skill.service;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Shared styling for Excel exports (in-memory workbooks — NASC-safe):
 * bold white header on dark fill, thin borders on every cell,
 * freeze the header row, and auto-sized columns.
 */
public final class ExcelExportStyle {

    private ExcelExportStyle() {
    }

    /** Bold white text on dark-blue fill, left-aligned, thin borders. */
    public static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorders(style);
        return style;
    }

    /** Plain data cell: thin borders, top-aligned. */
    public static CellStyle dataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.TOP);
        applyThinBorders(style);
        return style;
    }

    /** Data cell with wrapped text (long columns such as Description). */
    public static CellStyle wrapStyle(Workbook workbook) {
        CellStyle style = dataStyle(workbook);
        style.setWrapText(true);
        return style;
    }

    /** Freeze the header row (row 0). */
    public static void freezeHeader(Sheet sheet) {
        sheet.createFreezePane(0, 1);
    }

    /**
     * Auto-size every column in {@code lastColumn} (inclusive), with a
     * minimum width of 12 characters and an optional cap (in characters).
     */
    public static void autoSizeColumns(Sheet sheet, int lastColumn, int capChars) {
        for (int i = 0; i <= lastColumn; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            if (width < 12 * 256) {
                width = 12 * 256;
            }
            if (capChars > 0 && width > capChars * 256) {
                width = capChars * 256;
            }
            sheet.setColumnWidth(i, width);
        }
    }

    /** Convenience: write a string value with the given style. */
    public static void setStyled(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /** Convenience: write a number value with the given style. */
    public static void setStyled(Row row, int col, long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void applyThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}

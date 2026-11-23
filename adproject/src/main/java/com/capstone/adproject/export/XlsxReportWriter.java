package com.capstone.adproject.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Writes a {@link ReportDocument} as an Excel workbook: one sheet per table, the first one carrying the title and what the
 * report covers. Text is always written as text, never as a formula, so a name or comment that starts with "=" stays plain text.
 */
public class XlsxReportWriter {

    /** Excel refuses a cell longer than 32,767 characters. */
    private static final int MAX_CELL_CHARS = 32_000;
    private static final int MAX_SHEET_NAME = 31;

    public byte[] write(ReportDocument document) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Set<String> usedNames = new HashSet<>();
            List<ReportDocument.Section> sections = document.sections() == null ? List.of() : document.sections();
            if (sections.isEmpty()) {
                Sheet sheet = workbook.createSheet(uniqueName("Report", usedNames));
                writeHeading(sheet, 0, document, styles, true);
            }
            for (int i = 0; i < sections.size(); i++) {
                ReportDocument.Section section = sections.get(i);
                Sheet sheet = workbook.createSheet(uniqueName(section.heading() == null ? "Sheet " + (i + 1) : section.heading(), usedNames));
                int row = i == 0 ? writeHeading(sheet, 0, document, styles, true) : 0;
                writeSection(sheet, row, section, styles);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the spreadsheet.", e);
        }
    }

    /** Title, subtitle, the "what this covers" lines and when it was made. Returns the next free row. */
    private int writeHeading(Sheet sheet, int start, ReportDocument document, Styles styles, boolean withDetails) {
        int row = start;
        row = textRow(sheet, row, document.title(), styles.title);
        if (notBlank(document.subtitle())) {
            row = textRow(sheet, row, document.subtitle(), styles.plain);
        }
        if (withDetails && document.details() != null) {
            for (String line : document.details()) {
                row = textRow(sheet, row, line, styles.plain);
            }
        }
        row = textRow(sheet, row, "Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")), styles.muted);
        return row + 1;
    }

    private void writeSection(Sheet sheet, int start, ReportDocument.Section section, Styles styles) {
        int row = start;
        row = textRow(sheet, row, section.heading(), styles.sectionHeading);
        if (notBlank(section.note())) {
            row = textRow(sheet, row, section.note(), styles.muted);
        }
        List<String> columns = section.columns() == null ? List.of() : section.columns();
        int[] widest = new int[columns.size()];
        Row header = sheet.createRow(row++);
        for (int c = 0; c < columns.size(); c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(clip(columns.get(c)));
            cell.setCellStyle(styles.header);
            widest[c] = length(columns.get(c));
        }
        int headerRow = row - 1;
        List<List<Object>> rows = section.rows() == null ? List.of() : section.rows();
        for (List<Object> values : rows) {
            Row r = sheet.createRow(row++);
            for (int c = 0; c < values.size() && c < columns.size(); c++) {
                Object value = values.get(c);
                Cell cell = r.createCell(c);
                if (value instanceof Number number) {
                    double d = number.doubleValue();
                    if (Double.isFinite(d)) {
                        cell.setCellValue(d);
                        cell.setCellStyle(value instanceof Integer || value instanceof Long ? styles.integer : styles.decimal);
                    }
                    widest[c] = Math.max(widest[c], String.valueOf(value).length());
                } else if (value instanceof Boolean flag) {
                    cell.setCellValue(flag ? "Yes" : "No");
                    cell.setCellStyle(styles.body);
                } else if (value != null) {
                    String text = clip(String.valueOf(value));
                    cell.setCellValue(text);
                    cell.setCellStyle(styles.body);
                    widest[c] = Math.max(widest[c], length(text));
                }
            }
        }
        for (int c = 0; c < widest.length; c++) {
            sheet.setColumnWidth(c, Math.min(60, Math.max(10, widest[c] + 2)) * 256);
        }
        if (!columns.isEmpty()) {
            sheet.createFreezePane(0, headerRow + 1);
        }
    }

    private static int textRow(Sheet sheet, int rowIndex, String text, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(clip(text == null ? "" : text));
        cell.setCellStyle(style);
        return rowIndex + 1;
    }

    /** A sheet name Excel accepts: at most 31 characters, none of [ ] : * ? / \, not blank, and different from the others. */
    static String uniqueName(String wanted, Set<String> used) {
        String cleaned = (wanted == null ? "" : wanted).replaceAll("[\\[\\]:*?/\\\\]", " ").replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^'+|'+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "Sheet";
        }
        if (cleaned.length() > MAX_SHEET_NAME) {
            cleaned = cleaned.substring(0, MAX_SHEET_NAME).trim();
        }
        String name = cleaned;
        int n = 2;
        while (!used.add(name.toLowerCase())) {
            String suffix = " (" + n++ + ")";
            name = cleaned.substring(0, Math.min(cleaned.length(), MAX_SHEET_NAME - suffix.length())) + suffix;
        }
        return name;
    }

    private static String clip(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_CELL_CHARS ? value.substring(0, MAX_CELL_CHARS) : value;
    }

    private static int length(String value) {
        return value == null ? 0 : Math.min(value.length(), 60);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static final class Styles {
        final CellStyle title, plain, muted, sectionHeading, header, body, integer, decimal;

        Styles(Workbook workbook) {
            title = style(workbook, font(workbook, true, 14, IndexedColors.BLACK), null, false);
            plain = style(workbook, font(workbook, false, 11, IndexedColors.BLACK), null, false);
            muted = style(workbook, font(workbook, false, 10, IndexedColors.GREY_50_PERCENT), null, false);
            sectionHeading = style(workbook, font(workbook, true, 12, IndexedColors.BLACK), null, false);
            header = style(workbook, font(workbook, true, 11, IndexedColors.BLACK), IndexedColors.GREY_25_PERCENT, true);
            body = style(workbook, font(workbook, false, 11, IndexedColors.BLACK), null, true);
            integer = style(workbook, font(workbook, false, 11, IndexedColors.BLACK), null, true);
            integer.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            decimal = style(workbook, font(workbook, false, 11, IndexedColors.BLACK), null, true);
            decimal.setDataFormat(workbook.createDataFormat().getFormat("#,##0.0#"));
        }

        private static Font font(Workbook workbook, boolean bold, int size, IndexedColors color) {
            Font font = workbook.createFont();
            font.setBold(bold);
            font.setFontHeightInPoints((short) size);
            font.setColor(color.getIndex());
            return font;
        }

        private static CellStyle style(Workbook workbook, Font font, IndexedColors fill, boolean bordered) {
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            if (fill != null) {
                style.setFillForegroundColor(fill.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (bordered) {
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
            }
            return style;
        }
    }
}

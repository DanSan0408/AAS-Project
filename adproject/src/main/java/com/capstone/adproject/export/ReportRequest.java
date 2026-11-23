package com.capstone.adproject.export;

import java.util.ArrayList;
import java.util.List;

/**
 * A report the browser asks for: the tables it is already showing, sent back to be made into a file. This arrives from the
 * outside, so it is checked hard: how many tables, columns and rows, how long a text may be, and that every cell is plain text,
 * a number, true/false or empty. Nothing in it is trusted to choose a file name or anything else.
 */
public record ReportRequest(String title, String subtitle, List<String> details, List<SectionRequest> sections) {

    public record SectionRequest(String heading, String note, List<String> columns, List<List<Object>> rows) {}

    public static final int MAX_SECTIONS = 12;
    public static final int MAX_COLUMNS = 60;
    public static final int MAX_ROWS = 20_000;
    public static final int MAX_DETAILS = 40;
    public static final int MAX_TEXT = 1_000;

    /** @throws IllegalArgumentException with a message fit to show, when the request is too big or holds anything unexpected */
    public ReportDocument toDocument() {
        if (sections == null || sections.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to export.");
        }
        if (sections.size() > MAX_SECTIONS) {
            throw new IllegalArgumentException("A report can have at most " + MAX_SECTIONS + " tables.");
        }
        if (details != null && details.size() > MAX_DETAILS) {
            throw new IllegalArgumentException("Too many description lines.");
        }
        List<ReportDocument.Section> clean = new ArrayList<>();
        int totalRows = 0;
        for (SectionRequest section : sections) {
            if (section == null) {
                throw new IllegalArgumentException("A table is missing.");
            }
            List<String> columns = new ArrayList<>();
            if (section.columns() != null) {
                if (section.columns().size() > MAX_COLUMNS) {
                    throw new IllegalArgumentException("A table can have at most " + MAX_COLUMNS + " columns.");
                }
                section.columns().forEach(c -> columns.add(text(c)));
            }
            List<List<Object>> rows = new ArrayList<>();
            if (section.rows() != null) {
                totalRows += section.rows().size();
                if (totalRows > MAX_ROWS) {
                    throw new IllegalArgumentException("Too many rows to export (at most " + MAX_ROWS + "). Narrow the filters and try again.");
                }
                for (List<Object> row : section.rows()) {
                    List<Object> cells = new ArrayList<>();
                    if (row != null) {
                        for (int c = 0; c < row.size() && c < columns.size(); c++) {
                            cells.add(cell(row.get(c)));
                        }
                    }
                    rows.add(cells);
                }
            }
            clean.add(new ReportDocument.Section(text(section.heading()), blankToNull(text(section.note())), columns, rows));
        }
        List<String> cleanDetails = new ArrayList<>();
        if (details != null) {
            details.forEach(d -> cleanDetails.add(text(d)));
        }
        String cleanTitle = text(title).isBlank() ? "Report" : text(title);
        return new ReportDocument(cleanTitle, blankToNull(text(subtitle)), cleanDetails, clean);
    }

    private static Object cell(Object value) {
        if (value == null || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return Double.isFinite(d) ? value : null;
        }
        if (value instanceof CharSequence) {
            return text(value.toString());
        }
        throw new IllegalArgumentException("A table cell can only hold text, a number or true/false.");
    }

    private static String text(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() > MAX_TEXT ? trimmed.substring(0, MAX_TEXT) : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

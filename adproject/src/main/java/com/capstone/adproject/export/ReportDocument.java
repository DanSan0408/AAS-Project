package com.capstone.adproject.export;

import java.util.List;

/**
 * A downloadable report: a title, a few lines saying what it covers, and one or more tables. It knows nothing about files; the
 * writers turn it into .xlsx or .pdf. Cell values are text, numbers, booleans or null.
 */
public record ReportDocument(String title, String subtitle, List<String> details, List<Section> sections) {

    /** One table. {@code note} is an optional line under the heading (how to read it, or what was left out). */
    public record Section(String heading, String note, List<String> columns, List<List<Object>> rows) {}
}

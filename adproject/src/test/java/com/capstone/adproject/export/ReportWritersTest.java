package com.capstone.adproject.export;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The files really open: they are read back with the same libraries Excel-compatible and PDF tools rely on. */
class ReportWritersTest {

    private static ReportDocument.Section section(String heading, List<String> columns, List<Object>... rows) {
        return new ReportDocument.Section(heading, null, columns, Arrays.asList(rows));
    }

    private static List<Object> row(Object... cells) {
        return Arrays.asList(cells);
    }

    private static ReportDocument sample() {
        return new ReportDocument("Course Analytics", "SECJ4999 Capstone", List.of("Team: All teams", "Assessments: Final presentation"),
                List.of(section("Summary", List.of("Measure", "Value"), row("Students", 40), row("Average mark", 71.456), row("Passed", true)),
                        section("Needs attention", List.of("Student", "Reason"), row("Chen", "Below the pass mark"))));
    }

    private static XSSFWorkbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    // --- .xlsx -----------------------------------------------------------------------------------------------------------

    @Test
    void theWorkbookHasOneSheetPerTableAndTheFirstCarriesTheTitleAndWhatItCovers() throws Exception {
        try (XSSFWorkbook book = open(new XlsxReportWriter().write(sample()))) {
            assertEquals(2, book.getNumberOfSheets());
            assertEquals("Summary", book.getSheetName(0));
            assertEquals("Needs attention", book.getSheetName(1));
            Sheet first = book.getSheetAt(0);
            assertEquals("Course Analytics", first.getRow(0).getCell(0).getStringCellValue());
            assertEquals("SECJ4999 Capstone", first.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Team: All teams", first.getRow(2).getCell(0).getStringCellValue());
        }
    }

    @Test
    void numbersStayNumbersAndTextStaysText() throws Exception {
        try (XSSFWorkbook book = open(new XlsxReportWriter().write(sample()))) {
            Sheet sheet = book.getSheetAt(0);
            int header = headerRow(sheet, "Measure");
            assertEquals(CellType.NUMERIC, sheet.getRow(header + 1).getCell(1).getCellType());
            assertEquals(40.0, sheet.getRow(header + 1).getCell(1).getNumericCellValue());
            assertEquals(71.456, sheet.getRow(header + 2).getCell(1).getNumericCellValue(), 1e-9);
            assertEquals("Yes", sheet.getRow(header + 3).getCell(1).getStringCellValue(), "a true/false value reads as Yes/No");
            assertTrue(book.getFontAt(sheet.getRow(header).getCell(0).getCellStyle().getFontIndex()).getBold(), "the header row is bold");
        }
    }

    @Test
    void textThatLooksLikeAFormulaIsNeverEvaluated() throws Exception {
        ReportDocument doc = new ReportDocument("T", null, List.of(),
                List.of(section("S", List.of("Name"), row("=HYPERLINK(\"http://evil\",\"x\")"), row("+1+1"), row("@SUM(A1)"), row("-2+3"))));
        try (XSSFWorkbook book = open(new XlsxReportWriter().write(doc))) {
            Sheet sheet = book.getSheetAt(0);
            int header = headerRow(sheet, "Name");
            for (int i = 1; i <= 4; i++) {
                assertEquals(CellType.STRING, sheet.getRow(header + i).getCell(0).getCellType());
            }
            assertEquals("=HYPERLINK(\"http://evil\",\"x\")", sheet.getRow(header + 1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void sheetNamesAreMadeValidAndDifferent() throws Exception {
        ReportDocument doc = new ReportDocument("T", null, List.of(), List.of(
                section("Team A: Group / Individual?", List.of("x")),
                section("Team A: Group / Individual?", List.of("x")),
                section("A very long heading that is far longer than Excel allows for a sheet name", List.of("x")),
                section("", List.of("x"))));
        try (XSSFWorkbook book = open(new XlsxReportWriter().write(doc))) {
            Set<String> names = new HashSet<>();
            for (int i = 0; i < book.getNumberOfSheets(); i++) {
                String name = book.getSheetName(i);
                assertTrue(name.length() <= 31, name);
                assertFalse(name.matches(".*[\\[\\]:*?/\\\\].*"), name);
                assertTrue(names.add(name.toLowerCase()), "duplicate " + name);
            }
            assertEquals(4, names.size());
        }
    }

    @Test
    void anEmptyReportStillOpensAndAnInfiniteNumberLeavesAnEmptyCell() throws Exception {
        try (XSSFWorkbook empty = open(new XlsxReportWriter().write(new ReportDocument("Nothing", null, List.of(), List.of())))) {
            assertEquals(1, empty.getNumberOfSheets());
        }
        ReportDocument doc = new ReportDocument("T", null, List.of(), List.of(section("S", List.of("v"), row(Double.NaN), row(Double.POSITIVE_INFINITY))));
        try (XSSFWorkbook book = open(new XlsxReportWriter().write(doc))) {
            Sheet sheet = book.getSheetAt(0);
            int header = headerRow(sheet, "v");
            assertEquals(CellType.BLANK, sheet.getRow(header + 1).getCell(0).getCellType());
        }
    }

    @Test
    void aVeryLongCommentIsCutBeforeExcelWouldRefuseIt() throws Exception {
        ReportDocument doc = new ReportDocument("T", null, List.of(), List.of(section("S", List.of("c"), row("x".repeat(50_000)))));
        try (XSSFWorkbook book = open(new XlsxReportWriter().write(doc))) {
            Sheet sheet = book.getSheetAt(0);
            int header = headerRow(sheet, "c");
            assertTrue(sheet.getRow(header + 1).getCell(0).getStringCellValue().length() <= 32_000);
        }
    }

    private static int headerRow(Sheet sheet, String firstColumn) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            if (sheet.getRow(i) != null && sheet.getRow(i).getCell(0) != null
                    && firstColumn.equals(sheet.getRow(i).getCell(0).getStringCellValue())) {
                return i;
            }
        }
        throw new AssertionError("no header row " + firstColumn);
    }

    // --- .pdf ------------------------------------------------------------------------------------------------------------

    private static String pdfText(byte[] bytes) throws Exception {
        PdfReader reader = new PdfReader(bytes);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder all = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            all.append(extractor.getTextFromPage(page)).append('\n');
        }
        return all.toString();
    }

    @Test
    void thePdfIsARealDocumentWithTheTitleTablesAndAFooter() throws Exception {
        byte[] bytes = new PdfReportWriter().write(sample());

        assertEquals("%PDF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        String text = pdfText(bytes);
        assertTrue(text.contains("Course Analytics"), text);
        assertTrue(text.contains("SECJ4999 Capstone"));
        assertTrue(text.contains("Team: All teams"));
        assertTrue(text.contains("Needs attention"));
        assertTrue(text.contains("Below the pass mark"));
        assertTrue(text.contains("71.456"));
        assertTrue(text.contains("Yes"));
        assertTrue(text.matches("(?s).*Page 1 of\\s+1\\b.*"), "footer 'Page 1 of 1': " + text);
    }

    @Test
    void aLongTableRunsOverSeveralPagesAndRepeatsItsHeader() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            rows.add(row("Student " + i, i));
        }
        ReportDocument doc = new ReportDocument("Long", null, List.of(), List.of(new ReportDocument.Section("Everyone", null, List.of("Student", "Mark"), rows)));

        byte[] bytes = new PdfReportWriter().write(doc);

        PdfReader reader = new PdfReader(bytes);
        assertTrue(reader.getNumberOfPages() > 1);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        assertTrue(extractor.getTextFromPage(2).contains("Student"), "the header row is repeated on page 2");
        int pages = reader.getNumberOfPages();
        assertTrue(extractor.getTextFromPage(pages).matches("(?s).*Page " + pages + " of\\s+" + pages + "\\b.*"),
                "the last page says Page " + pages + " of " + pages);
    }

    @Test
    void charactersTheBuiltInFontCannotDrawBecomeQuestionMarksInsteadOfFailing() throws Exception {
        ReportDocument doc = new ReportDocument("名前 report", null, List.of(), List.of(section("S", List.of("Name"), row("Chen 陈伟"), row("Nurul Aïn José"))));

        byte[] bytes = new PdfReportWriter().write(doc);

        String text = pdfText(bytes);
        assertTrue(text.contains("Chen ??"), text);
        assertTrue(text.contains("Nurul A"), "Latin letters with accents are kept");
        assertEquals("a b", PdfReportWriter.safe("a\u0007b"), "control characters are dropped");
    }

    @Test
    void missingCellsAndAnEmptyTableDoNotBreakThePdf() throws Exception {
        ReportDocument doc = new ReportDocument("T", null, null, List.of(
                new ReportDocument.Section("Empty", "No rows yet", List.of("a", "b"), List.of()),
                new ReportDocument.Section("Short rows", null, List.of("a", "b"), List.of(row("only one"), row(null, null)))));

        String text = pdfText(new PdfReportWriter().write(doc));

        assertTrue(text.contains("Nothing to show."));
        assertTrue(text.contains("only one"));
        assertNotEquals(-1, text.indexOf("No rows yet"));
    }

    @Test
    void theFormatChoiceAcceptsOnlyXlsxOrPdfInAnyCase() {
        assertEquals(ReportFormat.XLSX, ReportFormat.parse("XLSX"));
        assertEquals(ReportFormat.PDF, ReportFormat.parse(" pdf "));
        for (String bad : new String[]{null, "", "csv", "docx"}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ReportFormat.parse(bad));
        }
    }

    @Test
    void aHeadingIsNeverLeftAloneAtTheBottomOfAPageWithItsTableOnTheNext() throws Exception {
        for (int filler = 5; filler <= 45; filler++) {
            List<List<Object>> many = new ArrayList<>();
            for (int i = 0; i < filler; i++) {
                many.add(row("Row " + i, i));
            }
            ReportDocument doc = new ReportDocument("T", null, List.of(), List.of(
                    new ReportDocument.Section("First", null, List.of("A1", "B1"), many),
                    new ReportDocument.Section("Second heading", null, List.of("A2", "B2"), List.of(row("x", 1), row("y", 2)))));
            PdfReader reader = new PdfReader(new PdfReportWriter().write(doc));
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                String text = extractor.getTextFromPage(page);
                if (text.contains("Second heading")) {
                    assertTrue(text.contains("A2"), "with " + filler + " rows the heading is alone on page " + page + ": " + text);
                }
            }
        }
    }

    @Test
    void aColumnOfNumbersIsRightAlignedButAColumnMixingTextAndNumbersIsNot() throws Exception {
        // checked through the document itself: both tables still read in full
        ReportDocument doc = new ReportDocument("T", null, List.of(), List.of(
                section("Mixed", List.of("Measure", "Value"), row("Average", 71.5), row("Reading", "On track")),
                section("Numbers", List.of("Name", "Mark"), row("Chen", 50), row("Maya", 60))));

        String text = pdfText(new PdfReportWriter().write(doc));

        assertTrue(text.contains("On track") && text.contains("71.5") && text.contains("Maya"));
    }
}

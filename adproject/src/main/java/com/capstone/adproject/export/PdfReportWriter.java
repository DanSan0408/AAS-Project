package com.capstone.adproject.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfTemplate;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Writes a {@link ReportDocument} as a landscape A4 PDF: the title and what the report covers, then each table with its
 * header repeated on every page, and "Page x of y" in the footer. The built-in Helvetica font covers Western European
 * text; any other character is shown as "?" instead of failing.
 */
public class PdfReportWriter {

    private static final CharsetEncoder WIN_ANSI = Charset.forName("windows-1252").newEncoder();
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font MUTED = new Font(Font.HELVETICA, 8, Font.NORMAL, new java.awt.Color(0x6B, 0x72, 0x80));
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font CELL_HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);

    public byte[] write(ReportDocument report) {
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 28, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new Footer());
            document.addTitle(safe(report.title()));
            document.open();

            document.add(new Paragraph(safe(report.title()), TITLE));
            if (notBlank(report.subtitle())) {
                document.add(new Paragraph(safe(report.subtitle()), TEXT));
            }
            if (report.details() != null) {
                for (String line : report.details()) {
                    document.add(new Paragraph(safe(line), TEXT));
                }
            }
            document.add(new Paragraph("Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")), MUTED));

            if (report.sections() != null) {
                for (ReportDocument.Section section : report.sections()) {
                    addSection(document, writer, section);
                }
            }
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not write the PDF.", e);
        }
    }

    /** A heading with fewer than this many points of room left under it (heading, header row and a couple of rows) starts a new page. */
    private static final float ROOM_FOR_A_HEADING_AND_ITS_FIRST_ROWS = 110;

    private void addSection(Document document, PdfWriter writer, ReportDocument.Section section) throws DocumentException {
        if (writer.getVerticalPosition(true) - document.bottomMargin() < ROOM_FOR_A_HEADING_AND_ITS_FIRST_ROWS) {
            document.newPage(); // never leave a heading alone at the bottom of a page with its table on the next
        }
        Paragraph heading = new Paragraph(safe(section.heading()), HEADING);
        heading.setSpacingBefore(14);
        heading.setKeepTogether(true);
        document.add(heading);
        if (notBlank(section.note())) {
            document.add(new Paragraph(safe(section.note()), MUTED));
        }
        List<String> columns = section.columns() == null ? List.of() : section.columns();
        if (columns.isEmpty()) {
            return;
        }
        PdfPTable table = new PdfPTable(widths(section));
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setHeaderRows(1);
        for (String column : columns) {
            PdfPCell cell = new PdfPCell(new Phrase(safe(column), CELL_HEAD));
            cell.setBackgroundColor(new java.awt.Color(0xE5, 0xE7, 0xEB));
            cell.setPadding(4);
            table.addCell(cell);
        }
        List<List<Object>> rows = section.rows() == null ? List.of() : section.rows();
        if (rows.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Nothing to show.", MUTED));
            empty.setColspan(columns.size());
            empty.setPadding(4);
            table.addCell(empty);
        }
        boolean[] numeric = numericColumns(columns.size(), rows);
        for (List<Object> values : rows) {
            for (int c = 0; c < columns.size(); c++) {
                Object value = c < values.size() ? values.get(c) : null;
                PdfPCell cell = new PdfPCell(new Phrase(safe(display(value)), CELL));
                cell.setPadding(3);
                if (numeric[c]) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                table.addCell(cell);
            }
        }
        document.add(table);
    }

    /** Right-align a column only when everything in it is a number (or empty): a column of mixed text and numbers reads left. */
    private static boolean[] numericColumns(int count, List<List<Object>> rows) {
        boolean[] numeric = new boolean[count];
        for (int c = 0; c < count; c++) {
            boolean any = false;
            boolean allNumbers = true;
            for (List<Object> row : rows) {
                Object value = c < row.size() ? row.get(c) : null;
                if (value instanceof Number) {
                    any = true;
                } else if (value != null && !String.valueOf(value).isEmpty()) {
                    allNumbers = false;
                }
            }
            numeric[c] = any && allNumbers;
        }
        return numeric;
    }

    /** Wider columns for longer text, none too narrow or too wide. */
    private static float[] widths(ReportDocument.Section section) {
        List<String> columns = section.columns();
        float[] weights = new float[columns.size()];
        for (int c = 0; c < weights.length; c++) {
            int widest = columns.get(c) == null ? 4 : Math.min(columns.get(c).length(), 40);
            if (section.rows() != null) {
                for (List<Object> row : section.rows()) {
                    if (c < row.size() && row.get(c) != null) {
                        widest = Math.max(widest, Math.min(display(row.get(c)).length(), 60));
                    }
                }
            }
            weights[c] = Math.max(6, widest);
        }
        return weights;
    }

    private static String display(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean flag) {
            return flag ? "Yes" : "No";
        }
        if (value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e12) {
            return String.valueOf(d.longValue());
        }
        return String.valueOf(value);
    }

    /** Text the built-in font can draw: other characters become "?". */
    static String safe(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\t') {
                out.append(cp == '\n' ? '\n' : ' ');
            } else if (cp < 0x10000 && Character.isISOControl((char) cp)) {
                out.append(' ');
            } else if (cp < 0x10000 && WIN_ANSI.canEncode((char) cp)) {
                out.append((char) cp);
            } else {
                out.append('?');
            }
        });
        return out.toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** "Page x of y" at the bottom of every page. */
    private static final class Footer extends PdfPageEventHelper {
        private PdfTemplate total;
        private BaseFont font;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 14);
            try {
                font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            String text = "Page " + writer.getPageNumber() + " of ";
            float x = page.getRight(document.rightMargin()) - 60;
            float y = document.bottomMargin() - 18;
            canvas.beginText();
            canvas.setFontAndSize(font, 8);
            canvas.setTextMatrix(x, y);
            canvas.showText(text);
            canvas.endText();
            canvas.addTemplate(total, x + font.getWidthPoint(text, 8), y - 3);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(font, 8);
            total.setTextMatrix(0, 3);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}

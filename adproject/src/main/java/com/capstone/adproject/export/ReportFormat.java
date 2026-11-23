package com.capstone.adproject.export;

import java.util.Locale;

import org.springframework.http.MediaType;

/** The two download formats. */
public enum ReportFormat {
    XLSX("xlsx", MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
    PDF("pdf", MediaType.APPLICATION_PDF);

    private final String extension;
    private final MediaType mediaType;

    ReportFormat(String extension, MediaType mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    /** @throws IllegalArgumentException for anything but xlsx or pdf (any case) */
    public static ReportFormat parse(String value) {
        if (value != null) {
            String wanted = value.trim().toLowerCase(Locale.ROOT);
            for (ReportFormat format : values()) {
                if (format.extension.equals(wanted)) {
                    return format;
                }
            }
        }
        throw new IllegalArgumentException("Choose xlsx or pdf.");
    }

    public byte[] write(ReportDocument document) {
        return this == XLSX ? new XlsxReportWriter().write(document) : new PdfReportWriter().write(document);
    }
}

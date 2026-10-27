package com.capstone.adproject.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService(tempDir.toString());
    }

    @Test
    void storePdfFile_validPdf_storesFileAndReturnsRelativePath() throws IOException {
        byte[] pdfContent = "%PDF-1.4 sample content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "my_report.pdf", "application/pdf", pdfContent);

        String relativePath = fileStorageService.storePdfFile(file, 10L, 20L, "group_1");

        assertNotNull(relativePath);
        assertTrue(relativePath.startsWith("course_10"));
        assertTrue(relativePath.endsWith(".pdf"));

        Resource resource = fileStorageService.loadFileAsResource(relativePath);
        assertTrue(resource.exists());
        assertTrue(resource.getFilename().endsWith(".pdf"));
    }

    @Test
    void storePdfFile_emptyFile_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storePdfFile(file, 10L, 20L, "group_1"));
    }

    @Test
    void storePdfFile_nonPdfExtension_throwsIllegalArgumentException() {
        byte[] content = "%PDF-1.4 malicious.exe disguised".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.exe", "application/octet-stream", content);

        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storePdfFile(file, 10L, 20L, "group_1"));
    }

    @Test
    void storePdfFile_invalidMagicBytes_throwsIllegalArgumentException() {
        byte[] content = "Plain text disguised as pdf".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", content);

        assertThrows(IllegalArgumentException.class, () ->
                fileStorageService.storePdfFile(file, 10L, 20L, "group_1"));
    }

    @Test
    void storePdfFile_pathTraversalFilename_sanitizedSafely() throws IOException {
        byte[] pdfContent = "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../evil_name.pdf", "application/pdf", pdfContent);

        String relativePath = fileStorageService.storePdfFile(file, 10L, 20L, "group_1");
        assertNotNull(relativePath);
        assertFalse(relativePath.contains(".."));
    }

    @Test
    void loadFileAsResource_pathTraversalAttempt_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                fileStorageService.loadFileAsResource("../../windows/system32/cmd.exe"));
    }

    @Test
    void deleteFile_existingFile_deletesSuccessfully() throws IOException {
        byte[] pdfContent = "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "delete_me.pdf", "application/pdf", pdfContent);

        String relativePath = fileStorageService.storePdfFile(file, 10L, 20L, "group_1");
        assertTrue(fileStorageService.loadFileAsResource(relativePath).exists());

        boolean deleted = fileStorageService.deleteFile(relativePath);
        assertTrue(deleted);
    }
}

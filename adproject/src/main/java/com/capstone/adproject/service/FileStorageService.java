package com.capstone.adproject.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService {

    private final Path rootLocation;

    public FileStorageService(@Value("${app.upload.submissions-dir:./uploads/submissions}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize submission storage directory at " + rootLocation, e);
        }
    }

    public String storePdfFile(MultipartFile file, Long courseId, Long assessmentId, String targetFolder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files (.pdf) are allowed.");
        }

        // Validate PDF magic bytes (%PDF-)
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[5];
            int read = is.read(header);
            if (read < 4 || header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) { // %PDF
                throw new IllegalArgumentException("Invalid file format. The uploaded file is not a valid PDF document.");
            }
        }

        // Sanitize folder path elements
        String safeCourse = "course_" + (courseId != null ? courseId : "common");
        String safeAssessment = "assessment_" + (assessmentId != null ? assessmentId : "common");
        String safeTarget = targetFolder != null ? targetFolder.replaceAll("[^a-zA-Z0-9_-]", "_") : "general";

        Path destinationDir = this.rootLocation.resolve(safeCourse).resolve(safeAssessment).resolve(safeTarget).normalize();
        
        // Security check: ensure destination is within rootLocation
        if (!destinationDir.startsWith(this.rootLocation)) {
            throw new SecurityException("Cannot store file outside upload directory.");
        }

        Files.createDirectories(destinationDir);

        String uniqueFileName = "sub_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
        Path destinationFile = destinationDir.resolve(uniqueFileName).normalize();

        if (!destinationFile.startsWith(destinationDir)) {
            throw new SecurityException("Path traversal attempt detected in filename.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // Return path relative to rootLocation
        return this.rootLocation.relativize(destinationFile).toString().replace('\\', '/');
    }

    public Resource loadFileAsResource(String relativePath) {
        try {
            if (relativePath == null || relativePath.contains("..")) {
                throw new SecurityException("Invalid file path.");
            }
            Path filePath = this.rootLocation.resolve(relativePath).normalize();
            if (!filePath.startsWith(this.rootLocation)) {
                throw new SecurityException("Cannot access file outside upload directory.");
            }
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + relativePath);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed file path: " + relativePath, e);
        }
    }

    public boolean deleteFile(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty() || relativePath.contains("..")) {
            return false;
        }
        try {
            Path filePath = this.rootLocation.resolve(relativePath).normalize();
            if (filePath.startsWith(this.rootLocation)) {
                return Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            // Ignore failure on deletion
        }
        return false;
    }
}


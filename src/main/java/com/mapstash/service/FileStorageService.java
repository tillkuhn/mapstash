package com.mapstash.service;

import com.mapstash.model.GpxFile;
import org.locationtech.jts.geom.Point;
import com.mapstash.repository.GpxFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path uploadDirectory;
    private final GpxFileRepository repository;
    private final GpxService gpxService;

    public FileStorageService(
            @Value("${mapstash.upload.directory:uploads}") String uploadDir,
            GpxFileRepository repository,
            GpxService gpxService) throws IOException {
        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.repository = repository;
        this.gpxService = gpxService;
        Files.createDirectories(uploadDirectory);
        log.info("Upload directory initialized at: {}", uploadDirectory);
    }

    /**
     * Store uploaded GPX file
     *
     * @param file The uploaded file
     * @param description Optional tour description
     * @return GpxFile metadata
     * @throws IOException if file cannot be stored
     */
    @Transactional
    public GpxFile storeFile(MultipartFile file, String description) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".gpx")) {
            throw new IllegalArgumentException("Only GPX files are allowed");
        }

        // Calculate checksum for duplicate detection
        String checksum;
        try {
            checksum = calculateChecksum(file.getInputStream());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to calculate file checksum", e);
        }

        // Check for duplicates
        Optional<GpxFile> existingFile = repository.findByChecksum(checksum);
        if (existingFile.isPresent()) {
            log.info("File {} already exists with checksum {}", originalFilename, checksum);
            GpxFile existing = existingFile.get();
            existing.setPath(uploadDirectory.resolve(existing.getFilename()).toString());
            return existing;
        }

        // Store file to disk
        String fileId = UUID.randomUUID().toString();
        String storedFilename = fileId + ".gpx";
        Path targetPath = uploadDirectory.resolve(storedFilename);

        Files.copy(file.getInputStream(), targetPath);
        log.info("Stored file {} as {}", originalFilename, storedFilename);

        // Extract name from GPX metadata or use filename without extension
        String name;
        try {
            name = gpxService.extractName(targetPath);
            if (name == null || name.trim().isEmpty()) {
                // Fallback to filename without extension
                name = originalFilename.replaceFirst("\\.gpx$", "");
            }
        } catch (Exception e) {
            log.warn("Failed to extract name from GPX metadata, using filename", e);
            name = originalFilename.replaceFirst("\\.gpx$", "");
        }

        // Save metadata to database
        GpxFile gpxFile = GpxFile.builder()
                .id(fileId)
                .filename(storedFilename)
                .originalFilename(originalFilename)
                .name(name)
                .uploadDate(LocalDateTime.now())
                .fileSize(file.getSize())
                .checksum(checksum)
                .description(description)
                .startPoint(gpxService.extractStartPoint(targetPath))
                .build();

        gpxFile = repository.save(gpxFile);
        gpxFile.setPath(targetPath.toString());

        return gpxFile;
    }

    /**
     * Get all stored GPX files
     *
     * @return List of GpxFile metadata
     */
    public List<GpxFile> listFiles() {
        List<GpxFile> files = repository.findAll(Sort.by(Sort.Direction.DESC, "uploadDate"));

        // Set transient path field for each file
        files.forEach(file -> {
            Path filePath = uploadDirectory.resolve(file.getFilename());
            file.setPath(filePath.toString());
        });

        return files;
    }

    /**
     * Get file path by ID
     *
     * @param fileId The file ID
     * @return Path to the file
     */
    public Path getFilePath(String fileId) {
        return uploadDirectory.resolve(fileId + ".gpx");
    }

    /**
     * Delete a file by ID
     *
     * @param fileId The file ID
     * @throws IOException if file cannot be deleted
     */
    @Transactional
    public void deleteFile(String fileId) throws IOException {
        // Delete from database first
        repository.deleteById(fileId);

        // Then delete from filesystem
        Path filePath = getFilePath(fileId);
        Files.deleteIfExists(filePath);
        log.info("Deleted file: {}", filePath);
    }

    /**
     * Calculate MD5 checksum of file
     *
     * @param inputStream Input stream of the file
     * @return MD5 checksum as hex string
     * @throws IOException if file cannot be read
     * @throws NoSuchAlgorithmException if MD5 algorithm is not available
     */
    private String calculateChecksum(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

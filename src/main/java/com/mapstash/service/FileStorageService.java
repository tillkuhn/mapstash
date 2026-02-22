package com.mapstash.service;

import com.mapstash.model.GpxFile;
import org.locationtech.jts.geom.Point;
import com.mapstash.repository.GpxFileRepository;
import com.mapstash.repository.GpxContentRepository;
import com.mapstash.model.GpxContent;
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
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Coordinate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path uploadDirectory;
    private final GpxFileRepository repository;
    private final GpxContentRepository contentRepository;
    private final GpxService gpxService;

    public FileStorageService(
            @Value("${mapstash.upload.directory:uploads}") String uploadDir,
            GpxFileRepository repository,
            GpxContentRepository contentRepository,
            GpxService gpxService) throws IOException {
        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.repository = repository;
        this.contentRepository = contentRepository;
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

        // Persist to DB (metadata + content). We still keep the uploads dir for compatibility
        String fileId = UUID.randomUUID().toString();
        String storedFilename = fileId + ".gpx";

        // Read bytes for content operations
        byte[] bytes = file.getBytes();

        // Extract name and start point using temporary InputStream
        String name = null;
        Point startPoint = null;
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            name = gpxService.extractName(in);
        } catch (Exception e) {
            log.warn("Failed to extract name from GPX metadata via stream, will try fallback", e);
        }

        if (name == null || name.trim().isEmpty()) {
            name = originalFilename.replaceFirst("\\.gpx$", "");
        }

        try (InputStream in2 = new java.io.ByteArrayInputStream(bytes)) {
            startPoint = gpxService.extractStartPoint(in2);
        } catch (Exception e) {
            log.warn("Failed to extract start point via stream, setting to POINT(0 0)", e);
            GeometryFactory gf = new GeometryFactory();
            Point pt = gf.createPoint(new Coordinate(0,0));
            pt.setSRID(4326);
            startPoint = pt;
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
                .startPoint(startPoint)
                .build();

        gpxFile = repository.save(gpxFile);

        // Save content in separate table
        GpxContent gpxContent = new GpxContent();
        gpxContent.setGpxFileId(gpxFile.getId());
        gpxContent.setGpxContent(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        contentRepository.save(gpxContent);

        // For compatibility keep a copy on disk (optional) - allows older deployments to still read files
        Path targetPath = uploadDirectory.resolve(storedFilename);
        Files.write(targetPath, bytes);
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
     * Return GeoJSON for a file by reading GPX content from gpx_contents and converting it.
     */
    public String getGeoJsonForFile(String fileId) throws IOException {
        GpxContent content = contentRepository.findByGpxFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("GPX content not found for file: " + fileId));
        try (InputStream in = new java.io.ByteArrayInputStream(content.getGpxContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return gpxService.convertToGeoJson(in);
        }
    }

    /**
     * Return bounding box for a file by reading GPX content and calculating bounds
     */
    public double[] getBoundsForFile(String fileId) throws IOException {
        GpxContent content = contentRepository.findByGpxFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("GPX content not found for file: " + fileId));
        try (InputStream in = new java.io.ByteArrayInputStream(content.getGpxContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return gpxService.calculateBounds(in);
        }
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

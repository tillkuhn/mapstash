package com.mapstash.service;

import com.mapstash.model.GpxContent;
import com.mapstash.model.GpxFile;
import com.mapstash.repository.GpxContentRepository;
import com.mapstash.repository.GpxFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class FileStorageService {

    private final GpxFileRepository repository;
    private final GpxContentRepository contentRepository;
    private final GpxService gpxService;
    private final ReadGpxJdbcService readGpxJdbcService;

    public FileStorageService(
            GpxFileRepository repository,
            GpxContentRepository contentRepository,
            GpxService gpxService,
            ReadGpxJdbcService readGpxJdbcService) {
        this.repository = repository;
        this.contentRepository = contentRepository;
        this.gpxService = gpxService;
        this.readGpxJdbcService = readGpxJdbcService;
        log.info("FileStorageService initialized (DB-backed content, no filesystem writes)");
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
            // No filesystem path - client should rely on DB-backed content
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
        String contentString = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        // Log content size and preview for debugging persistent issues (trim preview to 200 chars)
        int contentLen = contentString.length();
        String preview = contentLen > 200 ? contentString.substring(0, 200) + "..." : contentString;
        log.debug("Saving GPX content for fileId={} length={} preview={}", gpxFile.getId(), contentLen, preview.replaceAll("\n", "\\n"));

        // Validate that the content looks like GPX/XML and not a numeric value (previous bug stored numeric values accidentally)
        String trimmed = contentString.trim();
        if (trimmed.matches("^\\d+$") || !trimmed.startsWith("<")) {
            log.error("Refusing to persist invalid GPX content for fileId={} (length={}, preview={})", gpxFile.getId(), contentLen, preview.replaceAll("\n", "\\n"));
            throw new IllegalStateException("Invalid GPX content detected; aborting save to avoid corrupting database");
        }

        gpxContent.setGpxContent(contentString);
        // Use saveAndFlush to ensure content is written immediately to DB (helps with debugging and JDBC streaming)
        contentRepository.saveAndFlush(gpxContent);

        // Read back immediately and log what's actually persisted (post-save verification)
        Optional<GpxContent> persisted = contentRepository.findByGpxFileId(gpxFile.getId());
        if (persisted.isPresent()) {
            String stored = persisted.get().getGpxContent();
            int storedLen = (stored == null) ? 0 : stored.length();
            String storedPreview = (stored == null) ? "" : (storedLen > 200 ? stored.substring(0, 200) + "..." : stored);
            log.debug("Persisted GPX content for fileId={} storedLength={} preview={}", gpxFile.getId(), storedLen, storedPreview.replaceAll("\n", "\\n"));
            if (storedLen != contentLen) {
                log.warn("Mismatch between original content length={} and stored length={} for fileId={}", contentLen, storedLen, gpxFile.getId());
            }
        } else {
            log.error("Failed to read back GPX content after save for fileId={}", gpxFile.getId());
        }

        // No disk writes: store content only in DB and return metadata
        gpxFile.setPath(null);

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
        /*
        files.forEach(file -> {
            Path filePath = uploadDirectory.resolve(file.getFilename());
            file.setPath(filePath.toString());
        });
        */

        return files;
    }

    /**
     * Get file path by ID
     *
     * @param fileId The file ID
     * @return Path to the file
     */
    /*
    public Path getFilePath(String fileId) {
        return uploadDirectory.resolve(fileId + ".gpx");
    }

     */

    /**
     * Return GeoJSON for a file by reading GPX content from gpx_contents and converting it.
     */
    public String getGeoJsonForFile(String fileId) throws IOException {
        // Prefer streaming the content via JDBC for large content. Fall back to repository-stored content string.
        InputStream jdbcStream = readGpxJdbcService.streamGpxContent(fileId);
        if (jdbcStream != null) {
            try (InputStream in = jdbcStream) {
                return gpxService.convertToGeoJson(in);
            }
        }

        GpxContent content = contentRepository.findByGpxFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("GPX content not found for file: " + fileId));
        try (InputStream in2 = new java.io.ByteArrayInputStream(content.getGpxContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            return gpxService.convertToGeoJson(in2);
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
        /*
        Path filePath = getFilePath(fileId);
        Files.deleteIfExists(filePath);
         */
        log.info("Deleted file: {}", fileId);
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

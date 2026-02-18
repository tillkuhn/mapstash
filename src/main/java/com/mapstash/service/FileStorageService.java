package com.mapstash.service;

import com.mapstash.model.GpxFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileStorageService {

    private final Path uploadDirectory;

    public FileStorageService(@Value("${mapstash.upload.directory:uploads}") String uploadDir) throws IOException {
        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadDirectory);
        log.info("Upload directory initialized at: {}", uploadDirectory);
    }

    /**
     * Store uploaded GPX file
     *
     * @param file The uploaded file
     * @return GpxFile metadata
     * @throws IOException if file cannot be stored
     */
    public GpxFile storeFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".gpx")) {
            throw new IllegalArgumentException("Only GPX files are allowed");
        }

        String fileId = UUID.randomUUID().toString();
        String storedFilename = fileId + ".gpx";
        Path targetPath = uploadDirectory.resolve(storedFilename);

        Files.copy(file.getInputStream(), targetPath);
        log.info("Stored file {} as {}", originalFilename, storedFilename);

        GpxFile gpxFile = new GpxFile();
        gpxFile.setId(fileId);
        gpxFile.setFilename(storedFilename);
        gpxFile.setOriginalFilename(originalFilename);
        gpxFile.setUploadDate(LocalDateTime.now());
        gpxFile.setFileSize(file.getSize());
        gpxFile.setPath(targetPath.toString());

        return gpxFile;
    }

    /**
     * Get all stored GPX files
     *
     * @return List of GpxFile metadata
     * @throws IOException if directory cannot be read
     */
    public List<GpxFile> listFiles() throws IOException {
        List<GpxFile> files = new ArrayList<>();

        try (Stream<Path> paths = Files.list(uploadDirectory)) {
            paths.filter(path -> path.toString().toLowerCase().endsWith(".gpx"))
                    .forEach(path -> {
                        try {
                            String filename = path.getFileName().toString();
                            String fileId = filename.substring(0, filename.lastIndexOf('.'));

                            GpxFile gpxFile = new GpxFile();
                            gpxFile.setId(fileId);
                            gpxFile.setFilename(filename);
                            gpxFile.setOriginalFilename(filename);
                            gpxFile.setPath(path.toString());
                            gpxFile.setFileSize(Files.size(path));
                            gpxFile.setUploadDate(
                                    LocalDateTime.ofInstant(
                                            Files.getLastModifiedTime(path).toInstant(),
                                            java.time.ZoneId.systemDefault()
                                    )
                            );

                            files.add(gpxFile);
                        } catch (IOException e) {
                            log.error("Error reading file metadata for {}", path, e);
                        }
                    });
        }

        files.sort((a, b) -> b.getUploadDate().compareTo(a.getUploadDate()));
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
    public void deleteFile(String fileId) throws IOException {
        Path filePath = getFilePath(fileId);
        Files.deleteIfExists(filePath);
        log.info("Deleted file: {}", filePath);
    }
}

package com.mapstash.service;

import com.mapstash.model.GpxContent;
import com.mapstash.model.GpxFile;
import com.mapstash.repository.GpxContentRepository;
import com.mapstash.repository.GpxFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * One-time migration runner to import existing GPX files from uploads/ into the gpx_contents table.
 * Activate with profile: migration
 */
@Component
@Profile("migration")
@RequiredArgsConstructor
@Slf4j
public class GpxContentMigrationRunner implements CommandLineRunner {

    private final GpxFileRepository gpxFileRepository;
    private final GpxContentRepository gpxContentRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting GPX content migration (profile=migration)");

        List<GpxFile> files = gpxFileRepository.findAll();
        Path uploads = Paths.get("uploads").toAbsolutePath().normalize();

        for (GpxFile file : files) {
            if (gpxContentRepository.findByGpxFileId(file.getId()).isPresent()) {
                continue; // already migrated
            }

            Path p = uploads.resolve(file.getFilename());
            if (!Files.exists(p)) {
                log.warn("File {} not found in uploads directory, skipping", p);
                continue;
            }

            try {
                byte[] bytes = Files.readAllBytes(p);
                GpxContent content = new GpxContent();
                content.setGpxFileId(file.getId());
                content.setGpxContent(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                gpxContentRepository.save(content);
                log.info("Migrated content for file {}", file.getId());
            } catch (IOException e) {
                log.error("Failed to migrate file {}", p, e);
            }
        }

        log.info("GPX content migration completed");
    }
}

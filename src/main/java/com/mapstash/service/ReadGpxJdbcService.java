package com.mapstash.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class ReadGpxJdbcService {
    private final JdbcTemplate jdbcTemplate;

    public ReadGpxJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Stream GPX content as InputStream for large objects (TEXT).
     * Uses queryForObject to fetch the text and returns a ByteArrayInputStream.
     */
    public InputStream streamGpxContent(String gpxFileId) {
        try {
            String content = jdbcTemplate.queryForObject(
                    "SELECT gpx_content FROM gpx_contents WHERE gpx_file_id = ?",
                    new Object[]{gpxFileId}, String.class);
            if (content == null) return null;
            return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }
}

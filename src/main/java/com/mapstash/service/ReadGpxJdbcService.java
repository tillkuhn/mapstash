package com.mapstash.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class ReadGpxJdbcService {
    private final JdbcTemplate jdbcTemplate;

    public ReadGpxJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Stream GPX content as InputStream for large objects (TEXT).
     * Note: This uses ResultSet.getCharacterStream internally and wraps into InputStream.
     */
    public InputStream streamGpxContent(String gpxFileId) {
        return jdbcTemplate.query("SELECT gpx_content FROM gpx_contents WHERE gpx_file_id = ?",
                new Object[]{gpxFileId}, rs -> {
                    if (!rs.next()) return null;
                    java.io.Reader r = rs.getCharacterStream(1);
                    return new java.io.ReaderInputStream(r, java.nio.charset.StandardCharsets.UTF_8);
                });
    }
}

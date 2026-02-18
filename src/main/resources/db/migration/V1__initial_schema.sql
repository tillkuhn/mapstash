-- GPX Files Metadata Table
CREATE TABLE gpx_files (
    id VARCHAR(36) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(32) NOT NULL,
    upload_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast lookups by checksum (duplicate detection)
CREATE INDEX idx_gpx_files_checksum ON gpx_files(checksum);

-- Index for sorting by upload date
CREATE INDEX idx_gpx_files_upload_date ON gpx_files(upload_date DESC);

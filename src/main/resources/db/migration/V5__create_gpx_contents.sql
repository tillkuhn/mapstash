-- Add separate table to store GPX XML content as TEXT LOBs
CREATE TABLE gpx_contents (
  id BIGSERIAL PRIMARY KEY,
  gpx_file_id VARCHAR(36) NOT NULL UNIQUE,
  gpx_content TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT fk_gpx_file FOREIGN KEY (gpx_file_id) REFERENCES gpx_files(id) ON DELETE CASCADE
);

-- Ensure checksum uniqueness on gpx_files (if not present already)
CREATE UNIQUE INDEX IF NOT EXISTS ux_gpx_files_checksum ON gpx_files(checksum);

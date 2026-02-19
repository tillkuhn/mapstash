-- Add name field to gpx_files table
-- First add as nullable to populate existing records
ALTER TABLE gpx_files ADD COLUMN name VARCHAR(255);

-- Update existing records with name from original_filename (without extension)
UPDATE gpx_files
SET name = REGEXP_REPLACE(original_filename, '\.gpx$', '', 'i')
WHERE name IS NULL;

-- Now make it mandatory
ALTER TABLE gpx_files ALTER COLUMN name SET NOT NULL;

-- Add index for name searches
CREATE INDEX idx_gpx_files_name ON gpx_files(name);

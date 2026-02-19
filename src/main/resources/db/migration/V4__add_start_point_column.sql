-- Enable PostGIS extension if not already enabled
CREATE EXTENSION IF NOT EXISTS postgis;

-- Add start_point geometry column for tour start (SRID 4326, default POINT(0 0))
ALTER TABLE gpx_files
    ADD COLUMN start_point geometry(Point,4326) NOT NULL DEFAULT ST_GeomFromText('POINT(0 0)',4326);

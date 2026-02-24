# MapStash Database Persistence Plan

**Date:** 2026-02-18
**Status:** Planning - Not yet implemented

## Overview

This document outlines the database structure and implementation plan for persisting GPX data in PostgreSQL. Currently, MapStash stores files on the filesystem with no metadata persistence. This plan enables richer features like user ownership, track statistics, search, and multi-track comparisons.

## Database Schema

### Core Tables

#### 1. `gpx_files` (Main entity table)

Stores file metadata and computed statistics.

```sql
CREATE TABLE gpx_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(255) NOT NULL,           -- UUID filename on disk (e.g., "abc123.gpx")
    original_filename VARCHAR(255) NOT NULL,  -- User's original filename
    file_path VARCHAR(512) NOT NULL,          -- Full path to file on disk
    file_size BIGINT NOT NULL,                -- File size in bytes

    -- Metadata
    upload_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- GPX Metadata (extracted from GPX file)
    name VARCHAR(255),                        -- Track/route name from GPX
    description TEXT,                         -- Description from GPX metadata
    creator VARCHAR(255),                     -- GPX creator application

    -- Computed Statistics
    total_distance_meters DECIMAL(12, 2),    -- Total distance in meters
    total_elevation_gain_meters DECIMAL(10, 2), -- Cumulative elevation gain
    total_elevation_loss_meters DECIMAL(10, 2), -- Cumulative elevation loss
    min_elevation_meters DECIMAL(10, 2),      -- Lowest point
    max_elevation_meters DECIMAL(10, 2),      -- Highest point

    -- Bounding Box (for quick map centering)
    bbox_min_lon DECIMAL(10, 7),              -- West
    bbox_min_lat DECIMAL(10, 7),              -- South
    bbox_max_lon DECIMAL(10, 7),              -- East
    bbox_max_lat DECIMAL(10, 7),              -- North

    -- Counts
    track_count INTEGER DEFAULT 0,
    route_count INTEGER DEFAULT 0,
    waypoint_count INTEGER DEFAULT 0,
    total_point_count INTEGER DEFAULT 0,

    -- Future: User ownership (for authentication feature)
    -- user_id UUID REFERENCES users(id),

    CONSTRAINT file_size_positive CHECK (file_size > 0)
);

CREATE INDEX idx_gpx_files_upload_date ON gpx_files(upload_date DESC);
CREATE INDEX idx_gpx_files_original_filename ON gpx_files(original_filename);
-- CREATE INDEX idx_gpx_files_user_id ON gpx_files(user_id); -- When users added
```

#### 2. `gpx_tracks` (Track-level data)

Stores individual tracks within a GPX file. Most GPX files contain one track, but some may have multiple.

```sql
CREATE TABLE gpx_tracks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gpx_file_id UUID NOT NULL REFERENCES gpx_files(id) ON DELETE CASCADE,

    -- Track Metadata
    name VARCHAR(255),
    description TEXT,
    track_number INTEGER NOT NULL,            -- Order within the GPX file (1-based)
    segment_count INTEGER NOT NULL DEFAULT 0,

    -- Track Statistics
    distance_meters DECIMAL(12, 2),
    elevation_gain_meters DECIMAL(10, 2),
    elevation_loss_meters DECIMAL(10, 2),
    min_elevation_meters DECIMAL(10, 2),
    max_elevation_meters DECIMAL(10, 2),

    -- Time Data (if available in GPX)
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_seconds INTEGER,                 -- Computed from start/end time

    -- GeoJSON (pre-computed for fast retrieval)
    geojson_geometry JSONB,                   -- Stores LineString or MultiLineString

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT track_number_positive CHECK (track_number > 0),
    CONSTRAINT segment_count_positive CHECK (segment_count >= 0)
);

CREATE INDEX idx_gpx_tracks_file_id ON gpx_tracks(gpx_file_id);
CREATE INDEX idx_gpx_tracks_file_track ON gpx_tracks(gpx_file_id, track_number);
CREATE INDEX idx_gpx_tracks_start_time ON gpx_tracks(start_time) WHERE start_time IS NOT NULL;

-- GIN index for GeoJSON queries (optional, for future spatial queries)
CREATE INDEX idx_gpx_tracks_geojson ON gpx_tracks USING GIN (geojson_geometry);
```

#### 3. `gpx_routes` (Route-level data)

Routes are similar to tracks but typically represent planned routes rather than recorded tracks.

```sql
CREATE TABLE gpx_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gpx_file_id UUID NOT NULL REFERENCES gpx_files(id) ON DELETE CASCADE,

    -- Route Metadata
    name VARCHAR(255),
    description TEXT,
    route_number INTEGER NOT NULL,            -- Order within the GPX file (1-based)

    -- Route Statistics
    distance_meters DECIMAL(12, 2),
    min_elevation_meters DECIMAL(10, 2),
    max_elevation_meters DECIMAL(10, 2),

    -- GeoJSON (pre-computed)
    geojson_geometry JSONB,                   -- Stores LineString

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT route_number_positive CHECK (route_number > 0)
);

CREATE INDEX idx_gpx_routes_file_id ON gpx_routes(gpx_file_id);
CREATE INDEX idx_gpx_routes_file_route ON gpx_routes(gpx_file_id, route_number);
CREATE INDEX idx_gpx_routes_geojson ON gpx_routes USING GIN (geojson_geometry);
```

#### 4. `gpx_waypoints` (Waypoint data)

Points of interest within a GPX file.

```sql
CREATE TABLE gpx_waypoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gpx_file_id UUID NOT NULL REFERENCES gpx_files(id) ON DELETE CASCADE,

    -- Waypoint Data
    name VARCHAR(255),
    description TEXT,
    longitude DECIMAL(10, 7) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    elevation_meters DECIMAL(10, 2),

    -- Additional GPX waypoint fields
    symbol VARCHAR(100),                      -- Icon symbol name
    type VARCHAR(100),                        -- Waypoint type/category
    comment TEXT,

    -- Order
    waypoint_number INTEGER NOT NULL,         -- Order within the GPX file (1-based)

    -- GeoJSON (pre-computed)
    geojson_geometry JSONB,                   -- Stores Point

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT longitude_range CHECK (longitude >= -180 AND longitude <= 180),
    CONSTRAINT latitude_range CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT waypoint_number_positive CHECK (waypoint_number > 0)
);

CREATE INDEX idx_gpx_waypoints_file_id ON gpx_waypoints(gpx_file_id);
CREATE INDEX idx_gpx_waypoints_coords ON gpx_waypoints(longitude, latitude);
CREATE INDEX idx_gpx_waypoints_geojson ON gpx_waypoints USING GIN (geojson_geometry);
```

#### 5. `gpx_track_segments` (Optional: Track segment details)

For detailed segment-level analysis. **Consider this optional** - most use cases can work with track-level data.

```sql
CREATE TABLE gpx_track_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    track_id UUID NOT NULL REFERENCES gpx_tracks(id) ON DELETE CASCADE,

    segment_number INTEGER NOT NULL,          -- Order within the track (1-based)
    point_count INTEGER NOT NULL DEFAULT 0,

    -- Segment Statistics
    distance_meters DECIMAL(12, 2),
    elevation_gain_meters DECIMAL(10, 2),
    elevation_loss_meters DECIMAL(10, 2),

    -- Time Data
    start_time TIMESTAMP,
    end_time TIMESTAMP,

    -- GeoJSON (LineString for this segment)
    geojson_geometry JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT segment_number_positive CHECK (segment_number > 0),
    CONSTRAINT point_count_positive CHECK (point_count > 0)
);

CREATE INDEX idx_gpx_track_segments_track_id ON gpx_track_segments(track_id);
CREATE INDEX idx_gpx_track_segments_track_segment ON gpx_track_segments(track_id, segment_number);
```

#### 6. `gpx_track_points` (Optional: Raw point storage)

For storing raw GPS points. **Consider this optional** - storing in DB significantly increases size, but enables advanced queries (speed calculation, smoothing, point filtering).

```sql
-- Only implement if needed for:
-- - Point-level queries (e.g., "show me all points above 2000m elevation")
-- - Speed/pace calculations between points
-- - GPS track smoothing/filtering
-- - Exporting modified tracks

CREATE TABLE gpx_track_points (
    id BIGSERIAL PRIMARY KEY,                 -- Use BIGSERIAL for potentially millions of points
    segment_id UUID NOT NULL REFERENCES gpx_track_segments(id) ON DELETE CASCADE,

    -- Point Data
    longitude DECIMAL(10, 7) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    elevation_meters DECIMAL(10, 2),

    -- Order
    point_number INTEGER NOT NULL,            -- Order within segment (1-based)

    -- Timing (if available)
    timestamp TIMESTAMP,

    -- Computed fields (populated during processing)
    distance_from_previous_meters DECIMAL(10, 3), -- Distance since last point
    speed_mps DECIMAL(8, 3),                  -- Meters per second (if timestamp available)

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT point_number_positive CHECK (point_number > 0)
);

CREATE INDEX idx_gpx_track_points_segment_id ON gpx_track_points(segment_id);
CREATE INDEX idx_gpx_track_points_segment_point ON gpx_track_points(segment_id, point_number);
CREATE INDEX idx_gpx_track_points_timestamp ON gpx_track_points(timestamp) WHERE timestamp IS NOT NULL;

-- For spatial queries (requires PostGIS extension)
-- CREATE INDEX idx_gpx_track_points_location ON gpx_track_points USING GIST (ll_to_earth(latitude, longitude));
```

### Future: User Management Tables

```sql
-- For future authentication feature
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP
);

-- Add foreign key to gpx_files:
-- ALTER TABLE gpx_files ADD COLUMN user_id UUID REFERENCES users(id);
```

## Design Decisions

### 1. Pre-computed GeoJSON Storage

**Decision:** Store pre-computed GeoJSON in JSONB columns for tracks, routes, and waypoints.

**Rationale:**
- Faster API responses (no conversion needed)
- Reduces CPU load on repeated map views
- JSONB allows indexing and querying if needed
- Trade-off: Increased storage (~2x), but acceptable given modern storage costs

**Alternative considered:** Compute GeoJSON on-the-fly from points (current approach). Rejected due to performance concerns for large files.

### 2. Physical File Storage vs. BYTEA

**Decision:** Keep GPX files on filesystem, store file path in database.

**Rationale:**
- Maintains current filesystem-based storage
- Database focuses on metadata and computed data
- Easier backup/restore workflows (files separate from DB)
- Better performance for large files

**Alternative considered:** Store GPX XML as BYTEA or TEXT in database. Rejected due to storage overhead and backup complexity.

### 3. Statistics Computation Strategy

**Decision:** Compute statistics during upload and store in database.

**Implementation points:**
- Calculate distance using Haversine formula between consecutive points
- Calculate elevation gain/loss by summing positive/negative deltas
- Store results in `gpx_files`, `gpx_tracks`, and `gpx_routes` tables
- Future: Add "Reprocess" button to recalculate statistics

### 4. Cascade Deletion

**Decision:** Use `ON DELETE CASCADE` for all child tables.

**Rationale:**
- When user deletes a GPX file, all related data (tracks, routes, waypoints) should be deleted
- Maintains referential integrity automatically
- Physical file deletion handled separately in service layer

### 5. UUID Primary Keys

**Decision:** Use UUID (via `gen_random_uuid()`) for all primary keys except `gpx_track_points`.

**Rationale:**
- Globally unique identifiers (useful for future distributed systems)
- No sequential ID leakage
- Compatible with current filesystem naming (files already use UUIDs)
- Exception: `gpx_track_points` uses BIGSERIAL for performance with millions of rows

## Implementation Phases

### Phase 1: Core Persistence (Minimum Viable)

**Goal:** Replace filesystem-only storage with database metadata persistence.

**Scope:**
- Implement `gpx_files` table only
- JPA entities: `GpxFile` entity with JPA annotations
- Repository: `GpxFileRepository extends JpaRepository<GpxFile, UUID>`
- Update `FileStorageService` to save metadata to DB on upload
- Update `GpxController` to query DB instead of filesystem directory listing
- Keep GeoJSON conversion on-the-fly (no storage yet)

**Benefits:**
- Searchable file list
- Faster file list retrieval (no filesystem scanning)
- Foundation for future features

**Migration strategy:**
- Scan existing `uploads/` directory
- For each `.gpx` file, create DB record with metadata
- Run as Spring Boot `@Component` with `@PostConstruct` for one-time migration

### Phase 2: Track/Route/Waypoint Storage

**Goal:** Store detailed GPX elements for richer features.

**Scope:**
- Implement `gpx_tracks`, `gpx_routes`, `gpx_waypoints` tables
- JPA entities for each table
- Update `GpxService` to persist tracks/routes/waypoints during upload
- Store pre-computed GeoJSON in JSONB columns
- Update map view to fetch GeoJSON from DB instead of parsing file

**Benefits:**
- Multi-track comparison on single map
- Individual track/route viewing
- Faster map loading (no file parsing)

### Phase 3: Statistics & Search

**Goal:** Enable track statistics and search functionality.

**Scope:**
- Implement distance/elevation calculations in `GpxService`
- Store computed statistics in database
- Add search API: search by name, date range, distance range, elevation
- Display statistics on map view and file list

**Benefits:**
- "Show me all tracks longer than 20km"
- "Show me all tracks with >1000m elevation gain"
- Activity tracking and analysis

### Phase 4: Advanced Features (Optional)

**Scope:**
- Implement `gpx_track_segments` and `gpx_track_points` tables
- Point-level queries and filtering
- Track editing capabilities
- Speed/pace analysis
- User authentication and ownership (`users` table)

## Technology Stack

### Required Dependencies

Add to `pom.xml`:

```xml
<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Spring Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Flyway for database migrations (recommended) -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>

<!-- Optional: Hibernate spatial for advanced geo queries -->
<!--
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-spatial</artifactId>
</dependency>
-->
```

### Configuration

Add to `application.properties`:

```properties
# PostgreSQL Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/mapstash
spring.datasource.username=mapstash_user
spring.datasource.password=your_secure_password

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

### Database Setup

```bash
# Create PostgreSQL database and user
psql -U postgres

CREATE DATABASE mapstash;
CREATE USER mapstash_user WITH ENCRYPTED PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE mapstash TO mapstash_user;

# For PostgreSQL 15+, also grant schema privileges
\c mapstash
GRANT ALL ON SCHEMA public TO mapstash_user;
GRANT CREATE ON SCHEMA public TO mapstash_user;
```

### Flyway Migration Files

Structure:

```
src/main/resources/db/migration/
├── V1__create_gpx_files_table.sql
├── V2__create_gpx_tracks_table.sql
├── V3__create_gpx_routes_table.sql
├── V4__create_gpx_waypoints_table.sql
└── V5__create_indexes.sql
```

## Migration Strategy for Existing Data

### Automatic Migration on Startup

Create a `@Component` to migrate existing filesystem data:

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class FileSystemToDbMigration {

    private final FileStorageService fileStorageService;
    private final GpxFileRepository gpxFileRepository;
    private final GpxService gpxService;

    @Value("${mapstash.migration.enabled:false}")
    private boolean migrationEnabled;

    @PostConstruct
    public void migrate() {
        if (!migrationEnabled) {
            log.info("Database migration disabled (set mapstash.migration.enabled=true to enable)");
            return;
        }

        if (gpxFileRepository.count() > 0) {
            log.info("Database already contains data, skipping migration");
            return;
        }

        log.info("Starting migration of existing GPX files to database...");

        // Scan uploads directory
        // For each .gpx file:
        //   1. Parse metadata from filesystem
        //   2. Create GpxFile entity
        //   3. Save to database
        //   4. Optionally: parse and save tracks/routes/waypoints

        log.info("Migration completed");
    }
}
```

Enable with: `mapstash.migration.enabled=true` in `application-local.properties`

## API Changes

### Current API
- `GET /` - Returns list of files (scanned from filesystem)
- `POST /upload` - Saves file to filesystem
- `GET /map/{fileId}` - Parses file and renders map
- `POST /delete/{fileId}` - Deletes file from filesystem

### Updated API (Post-Implementation)
- `GET /` - Returns list of files (queried from database)
- `POST /upload` - Saves file to filesystem + saves metadata/tracks/routes/waypoints to database
- `GET /map/{fileId}` - Fetches GeoJSON from database (no file parsing)
- `POST /delete/{fileId}` - Deletes file from filesystem + CASCADE deletes from database
- **NEW:** `GET /api/files?search={query}` - Search files by name/metadata
- **NEW:** `GET /api/tracks?minDistance={km}&maxDistance={km}` - Filter tracks by criteria
- **NEW:** `GET /api/stats/{fileId}` - Get track statistics

## Testing Considerations

### Unit Tests
- Test distance calculations (Haversine formula)
- Test elevation gain/loss calculations
- Test GeoJSON generation and storage
- Test repository queries

### Integration Tests
- Test file upload with DB persistence
- Test file deletion (filesystem + DB)
- Test migration from filesystem to DB
- Test GeoJSON retrieval performance

### Performance Tests
- Benchmark: GeoJSON from DB vs. on-the-fly parsing
- Test with large GPX files (>10MB, >100k points)
- Test multi-track file handling

## Future Enhancements Beyond This Plan

1. **PostGIS Extension** - For advanced spatial queries (proximity, intersections)
2. **Track Comparison** - Overlay multiple tracks on one map
3. **Heatmaps** - Aggregate all tracks to show frequently traveled routes
4. **Export Formats** - Export tracks to KML, KMZ, GeoJSON files
5. **Track Editing** - Trim, merge, or split tracks in the UI
6. **Tile Caching** - Cache map tiles for offline viewing

## References

- GPX Format: https://www.topografix.com/gpx.asp
- GeoJSON Spec: https://datatracker.ietf.org/doc/html/rfc7946
- Haversine Formula: https://en.wikipedia.org/wiki/Haversine_formula
- Spring Data JPA: https://spring.io/projects/spring-data-jpa
- Flyway: https://flywaydb.org/documentation/
- PostgreSQL JSONB: https://www.postgresql.org/docs/current/datatype-json.html

---

**Next Steps:**
1. Review and approve this plan
2. Set up local PostgreSQL database
3. Implement Phase 1 (Core Persistence)
4. Test with existing GPX files
5. Iterate on Phases 2-4 based on requirements

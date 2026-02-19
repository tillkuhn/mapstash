# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MapStash is a Spring Boot application for storing and visualizing GPX (GPS Exchange Format) files on interactive maps using Mapbox GL JS. The application uses **server-side rendering** with Thymeleaf templates - there is no separate frontend build process.

**Key Technologies:**
- Spring Boot 4.0.0 with Java 25
- PostgreSQL database with Spring Data JPA
- Flyway for database migrations
- Thymeleaf for server-side HTML rendering
- Mapbox GL JS v3.1.0 (client-side map rendering in browser)
- JPX library (`io.jenetics:jpx`) for GPX parsing
- GeoJSON as the interchange format between backend and frontend

**Build Tool:**
- Maven via SDKMAN (no Maven wrapper used)
- Use `mvn` commands directly, NOT `./mvnw`

**Code Generation:**
- Lombok used for reducing boilerplate code (`@Data`, `@Builder`, `@AllArgsConstructor`, etc.)
- Requires IDE plugin (IntelliJ IDEA Lombok plugin, VS Code Lombok extension) for proper syntax support
- Annotation processing configured in Maven compiler plugin

## Build & Run Commands

### Development (Preferred: Use Makefile)
```bash
# Run with hot reload (DevTools enabled) - uses local profile (port 4200)
make run

# Or use Maven directly
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Access application
http://localhost:4200  # Local development (local profile active)
http://localhost:8080  # Production default
```

### Build
```bash
# Using Makefile (Preferred)
make build              # Full build with tests
make build-skip-tests   # Build without tests (faster)
make jar                # Alias for 'make build'

# Using Maven directly
mvn clean package               # Full build
mvn clean package -DskipTests   # Build without tests

# Run the JAR
java -jar target/mapstash-0.1.0-SNAPSHOT.jar
```

### Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=GpxServiceTest

# Run specific test method
mvn test -Dtest=GpxServiceTest#testConvertToGeoJson
```

### GraalVM Native Image Build

**IMPORTANT: This project supports GraalVM Native Image compilation for fast startup and low memory footprint.**

**Prerequisites:**
```bash
# Install GraalVM 25+ via SDKMAN
sdk install java 25.0.2-graalce
sdk use java 25.0.2-graalce

# CRITICAL: Export JAVA_HOME explicitly for Maven
export JAVA_HOME=/Users/tillkuhn/.sdkman/candidates/java/25.0.2-graalce
```

**Build Native Image:**
```bash
# Full build with native compilation (takes ~90 seconds)
export JAVA_HOME=/Users/tillkuhn/.sdkman/candidates/java/25.0.2-graalce
mvn clean package -Pnative -DskipTests

# Output: target/mapstash (executable, ~154MB)
```

**Run Native Image:**
```bash
./target/mapstash
# Application starts on port 8080 (production default)
```

**Native Image Benefits:**
- ⚡ Fast startup: ~0.1 seconds (vs ~3 seconds JVM)
- 💾 Low memory: ~50-100MB RSS (vs ~300-500MB JVM)
- 📦 Single executable: No JVM installation required

**Reflection Configuration:**

The application uses Thymeleaf templates which require reflection for dynamic method calls (e.g., `${list.isEmpty()}`).

**✅ CORRECT APPROACH (Spring Boot Native):**

Use `RuntimeHintsRegistrar` to register reflection hints programmatically:

- **Location**: `src/main/java/com/mapstash/config/NativeRuntimeHints.java`
- **Why**: Spring Boot's AOT process generates its own `reachability-metadata.json` and would overwrite custom JSON files
- **Benefit**: Type-safe, IDE-friendly, integrates with Spring's native image processing

**Current configuration** registers reflection for: `ArrayList`, `List`, `Collection`, `Iterable`, `String`, `CharSequence`

**Adding New Reflection Requirements:**

If you encounter `MissingReflectionRegistrationError` at runtime:

1. Add the type to `NativeRuntimeHints.java`:
   ```java
   hints.reflection()
       .registerType(YourClass.class,
           MemberCategory.INVOKE_PUBLIC_METHODS);
   ```

2. Rebuild the native image (hints are processed at compile time)

**❌ AVOID: Manual JSON configuration files**

Do NOT create `reflect-config.json` or `reachability-metadata.json` in `META-INF/native-image/` - Spring Boot's AOT process either ignores them or overwrites them. Use `RuntimeHintsRegistrar` instead.

**Troubleshooting Native Build:**

- **Maven can't find native-image**: Ensure `JAVA_HOME` points to GraalVM distribution
  ```bash
  echo $JAVA_HOME
  $JAVA_HOME/bin/native-image --version  # Should show GraalVM version
  ```

- **Reflection errors at runtime**: Add missing classes to `reflect-config.json` and rebuild

- **Build runs out of memory**: Increase heap in `pom.xml` native profile:
  ```xml
  <buildArg>-J-Xmx16g</buildArg>
  ```

- **Resource not found errors**: Add patterns to `resource-config.json` in same directory

## Configuration Requirements

**CRITICAL: Mapbox Token Required**

The application requires a valid Mapbox API token to function. Without it, maps will not render.

Three configuration options (in order of preference):

1. **Environment Variable (Recommended for development):**
   ```bash
   export MAPBOX_TOKEN=pk.your-token-here
   make run
   ```

2. **Local Properties File (Recommended for local config):**
   ```bash
   cp src/main/resources/application-local.properties.template src/main/resources/application-local.properties
   # Edit application-local.properties and set:
   # server.port=4200
   # mapstash.mapbox.token=pk.your-token-here
   # spring.datasource.url=jdbc:postgresql://localhost:5432/mapstash_db
   # spring.datasource.username=your-db-username
   # spring.datasource.password=your-db-password
   # (For default local setup, use 'mapstash' for both username and password)
   ```

3. **Direct in application.properties (NOT for version control):**
   Edit `mapstash.mapbox.token` property (ensure it stays in .gitignore patterns)

Get tokens from: https://account.mapbox.com/access-tokens/

**Database Configuration:**

The application requires PostgreSQL with PostGIS enabled for storing GPX file metadata and spatial columns:
- PostGIS extension is required and initialized automatically (see Flyway migration)
- New `start_point` column in `gpx_files` enables spatial queries and mapping


1. **Database Setup:**
   ```bash
   # Create database and user (if not exists)
   createdb mapstash_db
   createuser mapstash
   psql -c "ALTER USER mapstash WITH PASSWORD 'mapstash';"
   psql -c "GRANT ALL PRIVILEGES ON DATABASE mapstash_db TO mapstash;"
   ```

2. **Configuration:**
   - Database URL, username, and password can be set via environment variables:
     - `DATABASE_URL` (default: `jdbc:postgresql://localhost:5432/mapstash_db`)
     - `DATABASE_USER` (default: `mapstash`)
     - `DATABASE_PASSWORD` (default: `mapstash`)
   - Or configure in `application-local.properties` (see template)

3. **Schema Management:**
   - Flyway handles all database migrations automatically on startup
   - Migration files located in `src/main/resources/db/migration`
   - First run will create the `gpx_files` table with indexes
   - `spring.flyway.baseline-on-migrate=true` allows working with existing schemas

**Profile Configuration:**
- **Local profile** (development): Activated via `make run` or `-Dspring-boot.run.profiles=local`, uses port 4200
- **Production**: No profile needed, uses default port 8080
- The local profile ensures development settings don't affect deployments

## Architecture

### Request Flow

```
Browser → GpxController → Services → Response
                ↓
         Thymeleaf Template
                ↓
         HTML + JavaScript (Mapbox GL JS)
```

### Core Components

**GpxController** (`controller/GpxController.java`)
- Single controller handling all web requests
- Injects `mapboxToken` from configuration into templates
- Returns Thymeleaf template names (not JSON)
- Exception: `/api/gpx/{fileId}` returns raw GeoJSON string

**GpxService** (`service/GpxService.java`)
- Converts GPX to GeoJSON using JPX library
- Handles three GPX element types: tracks (most common), routes, waypoints
- Track-specific logic: Multi-segment tracks → MultiLineString, single-segment → LineString
- Calculates bounding boxes for auto-zoom functionality
- Uses Jackson ObjectMapper to build GeoJSON programmatically

**FileStorageService** (`service/FileStorageService.java`)
- Hybrid storage: GPX files on filesystem, metadata in PostgreSQL database
- UUID-based file naming: `{uuid}.gpx`
- Storage location: `uploads/` directory (configurable via `mapstash.upload.directory`)
- MD5 checksum calculation for duplicate detection on upload
- File metadata persisted via `GpxFileRepository` (Spring Data JPA)

**GpxFileRepository** (`repository/GpxFileRepository.java`)
- Spring Data JPA repository for database operations
- Provides `findByChecksum()` method for duplicate detection
- Supports sorting by upload date for file listing

**GpxFile** (`model/GpxFile.java`)
- JPA entity mapped to `gpx_files` table
- Stores: id, filename, originalFilename, fileSize, checksum, uploadDate, timestamps
- `path` field is `@Transient` (not persisted, calculated from uploadDirectory + id + ".gpx")
- Audit timestamps (`createdAt`, `updatedAt`) managed by JPA auditing

### Data Flow: GPX → GeoJSON → Map

1. User uploads `.gpx` file → FileStorageService calculates MD5 checksum
2. Service checks database for duplicate by checksum (prevents re-upload)
3. If unique, file stored to disk and metadata saved to PostgreSQL via JpaRepository
4. Controller calls GpxService to parse file
5. JPX library reads GPX XML → Java objects (GPX, Track, Route, WayPoint)
6. GpxService converts to GeoJSON FeatureCollection with properties:
   - Tracks/Routes: LineString or MultiLineString with `type: "track"` or `type: "route"`
   - Waypoints: Point with `type: "waypoint"`, optional name/description
7. GeoJSON passed to Thymeleaf template as model attribute
8. Template embeds GeoJSON in `<script>` tag using `th:inline="javascript"`
9. Client-side Mapbox GL JS renders map from GeoJSON

### Frontend Architecture

**Server-Side Rendering (Thymeleaf):**
- `templates/index.html`: Home page with upload form and file list
- `templates/map.html`: Map visualization page with embedded Mapbox GL JS
- `templates/error.html`: Error page

**Client-Side (In Templates):**
- Mapbox GL JS loaded from CDN in `map.html`
- JavaScript embedded directly in templates (no separate JS files)
- Thymeleaf `th:inline="javascript"` passes server data to client scripts
- CSS embedded in `<style>` tags in templates (no separate CSS files)

### File Storage Model

- **Hybrid Storage Approach**: GPX files stored on filesystem, metadata in PostgreSQL database
- **File Naming**: UUID-based (e.g., `{uuid}.gpx`) prevents collisions
- **Duplicate Detection**: MD5 checksum calculated on upload, checked against database before storing
- **Metadata Persistence**: `GpxFile` entity persisted via Spring Data JPA repository
- **Transient Path Field**: `path` attribute calculated dynamically, not stored in database
- **Audit Trail**: JPA auditing automatically tracks `createdAt` and `updatedAt` timestamps
- **File Listing**: Sorted by `uploadDate` DESC, queried directly from database (no filesystem scan)

## API Endpoints

| Method | Path | Purpose | Returns |
|--------|------|---------|---------|
| GET | `/` | Home page | Thymeleaf template |
| POST | `/upload` | File upload | Redirect to map view |
| GET | `/map/{fileId}` | Map visualization | Thymeleaf template |
| POST | `/delete/{fileId}` | Delete file | Redirect to home |
| GET | `/api/gpx/{fileId}` | Get GeoJSON | Raw JSON string |

## Feature Development Workflow

**PRD (Product Requirements Document):**
- Feature requirements are tracked in **PRD.md** with unique IDs
- When implementing features:
  1. User references requirement by ID (e.g., "Implement REQ-001")
  2. Implement the feature according to the description
  3. Update status in PRD.md from ❌ Not Implemented to ✅ Implemented
  4. Add commit reference in PRD.md when marking as implemented

**Example workflow:**
```bash
# User requests: "Implement REQ-001"
# 1. Read PRD.md to understand requirement
# 2. Implement feature
# 3. Update PRD.md status column to: ✅ Implemented (commit abc1234)
```

## Development Patterns

### Adding New Features

When adding features that modify GPX visualization:
1. Update `GpxService.convertToGeoJson()` to include new data in GeoJSON
2. Modify `map.html` template to consume and render new data
3. Update map layers/styling in Mapbox GL JS JavaScript

When adding new file operations:
1. Add method to `FileStorageService`
2. Add controller endpoint in `GpxController`
3. Update relevant Thymeleaf template

### Thymeleaf + Mapbox Integration Pattern

Critical pattern for passing data to Mapbox GL JS:

```html
<script th:inline="javascript">
    /*<![CDATA[*/
    const geoJsonData = /*[[${geoJson}]]*/ '';
    const geoJson = typeof geoJsonData === 'string' ? JSON.parse(geoJsonData) : geoJsonData;

    // Use in Mapbox
    map.addSource('gpx-data', {
        type: 'geojson',
        data: geoJson
    });
    /*]]>*/
</script>
```

Always use `th:inline="javascript"` and `/*[[${variable}]]*/` syntax for server-to-client data transfer.

### Hot Reload Behavior

- **Java changes**: Auto-restart via Spring Boot DevTools
- **Template changes**: Immediate (no restart) - refresh browser
- **Properties changes**: Requires manual restart
- **Local profile**: Automatically active when using `make run`

## Common Troubleshooting

**Map displays blank/doesn't load:**
- Check browser console for Mapbox token errors
- Verify `mapstash.mapbox.token` is set and valid
- Check Network tab for failed Mapbox API requests

**File upload fails with large files:**
- Check `spring.servlet.multipart.max-file-size` (default: 50MB)
- Verify disk space in `uploads/` directory

**GeoJSON conversion errors:**
- Verify GPX file is valid XML
- Check GPX contains at least one track, route, or waypoint
- Review logs for JPX parsing exceptions

**Database connection errors:**
- Verify PostgreSQL is running: `pg_isready`
- Check database exists: `psql -l | grep mapstash_db`
- Verify credentials in `application-local.properties`
- Check Flyway migration logs on startup for schema issues

**Duplicate upload errors:**
- Application automatically detects duplicates via MD5 checksum
- If same file uploaded again, existing record is returned (no error)
- To force re-upload, delete existing file first

## Future Enhancement Areas

See README.md "Future Enhancements" section. Priority areas:
- ✅ Database integration for metadata persistence (REQ-008 - Implemented)
- Track statistics calculation (distance, elevation gain)
- Multi-track comparison on single map
- User authentication and file ownership
- Full-text search and filtering (requires database - now available)

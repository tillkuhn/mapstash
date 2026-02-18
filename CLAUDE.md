# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MapStash is a Spring Boot application for storing and visualizing GPX (GPS Exchange Format) files on interactive maps using Mapbox GL JS. The application uses **server-side rendering** with Thymeleaf templates - there is no separate frontend build process.

**Key Technologies:**
- Spring Boot 4.0.0 with Java 25
- Thymeleaf for server-side HTML rendering
- Mapbox GL JS v3.1.0 (client-side map rendering in browser)
- JPX library (`io.jenetics:jpx`) for GPX parsing
- GeoJSON as the interchange format between backend and frontend

**Build Tool:**
- Maven via SDKMAN (no Maven wrapper used)
- Use `mvn` commands directly, NOT `./mvnw`

## Build & Run Commands

### Development
```bash
# Run with hot reload (DevTools enabled)
mvn spring-boot:run

# Access application
http://localhost:8080
```

### Build
```bash
# Full build
mvn clean package

# Build without tests
mvn clean package -DskipTests

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

## Configuration Requirements

**CRITICAL: Mapbox Token Required**

The application requires a valid Mapbox API token to function. Without it, maps will not render.

Three configuration options (in order of preference):

1. **Environment Variable (Recommended for development):**
   ```bash
   export MAPBOX_TOKEN=pk.your-token-here
   mvn spring-boot:run
   ```

2. **Local Properties File (Recommended for local config):**
   ```bash
   cp src/main/resources/application-local.properties.template src/main/resources/application-local.properties
   # Edit application-local.properties and set: mapstash.mapbox.token=pk.your-token-here
   ```

3. **Direct in application.properties (NOT for version control):**
   Edit `mapstash.mapbox.token` property (ensure it stays in .gitignore patterns)

Get tokens from: https://account.mapbox.com/access-tokens/

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
- Filesystem-based storage (no database)
- UUID-based file naming: `{uuid}.gpx`
- Storage location: `uploads/` directory (configurable via `mapstash.upload.directory`)
- File metadata extracted from filesystem attributes

### Data Flow: GPX → GeoJSON → Map

1. User uploads `.gpx` file → FileStorageService stores to disk
2. Controller calls GpxService to parse file
3. JPX library reads GPX XML → Java objects (GPX, Track, Route, WayPoint)
4. GpxService converts to GeoJSON FeatureCollection with properties:
   - Tracks/Routes: LineString or MultiLineString with `type: "track"` or `type: "route"`
   - Waypoints: Point with `type: "waypoint"`, optional name/description
5. GeoJSON passed to Thymeleaf template as model attribute
6. Template embeds GeoJSON in `<script>` tag using `th:inline="javascript"`
7. Client-side Mapbox GL JS renders map from GeoJSON

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

- Files stored by UUID, not original filename (prevents collisions)
- No database: Metadata reconstructed from filesystem on each request
- `GpxFile` model object created transiently, not persisted
- File list sorted by upload date (descending) on retrieval

## API Endpoints

| Method | Path | Purpose | Returns |
|--------|------|---------|---------|
| GET | `/` | Home page | Thymeleaf template |
| POST | `/upload` | File upload | Redirect to map view |
| GET | `/map/{fileId}` | Map visualization | Thymeleaf template |
| POST | `/delete/{fileId}` | Delete file | Redirect to home |
| GET | `/api/gpx/{fileId}` | Get GeoJSON | Raw JSON string |

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

## Future Enhancement Areas

See README.md "Future Enhancements" section. Priority areas:
- Database integration for metadata persistence
- Track statistics calculation (distance, elevation gain)
- Multi-track comparison on single map
- User authentication and file ownership

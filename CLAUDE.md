# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Summary
- Project: MapStash — Spring Boot app that stores and visualizes GPX files using Mapbox GL JS and server-side Thymeleaf templates.
- Build: Maven (use system mvn; project expects SDKMAN-managed JDKs for GraalVM when building native images).
- Key runtime requirements: PostgreSQL with PostGIS, a valid Mapbox access token.

Quick commands (cheat sheet)
- Development (hot reload, local profile, preferred):
  make run
  or
  mvn spring-boot:run -Dspring-boot.run.profiles=local

- Build:
  make build              # full build with tests
  make build-skip-tests   # build without tests
  make jar                # alias for build

  mvn clean package               # full build
  mvn clean package -DskipTests   # build without tests

- Run artifact:
  java -jar target/mapstash-0.1.0-SNAPSHOT.jar

- GraalVM native build (short):
  export JAVA_HOME=/path/to/graalvm
  mvn clean package -Pnative -DskipTests
  ./target/mapstash

- Tests:
  mvn test
  mvn test -Dtest=GpxServiceTest
  mvn test -Dtest=GpxServiceTest#testConvertToGeoJson

- Quick sanity / debug commands:
  pg_isready
  echo $MAPBOX_TOKEN
  curl -s http://localhost:4200/api/gpx/{fileId}    # fetch raw GeoJSON for a file

High-level architecture (big picture)
- Single-module Spring Boot web application with server-side rendered pages (Thymeleaf). No separate frontend build.
- Main responsibilities:
  - Web layer: GpxController handles web routes and template rendering (also exposes GET /api/gpx/{fileId} for raw GeoJSON).
  - Service layer: GpxService converts GPX → GeoJSON; FileStorageService manages on-disk GPX files and MD5 duplicate detection.
  - Persistence: GpxFile JPA entity persisted via GpxFileRepository (PostgreSQL + PostGIS).
  - Templates: Thymeleaf templates under src/main/resources/templates render HTML and embed GeoJSON via th:inline="javascript" for Mapbox.
  - Mapping: Client-side Mapbox GL JS (loaded from CDN inside templates) renders GeoJSON provided by server.

Important files to read first
- src/main/java/com/mapstash/controller/GpxController.java — web routes, template model attributes
- src/main/java/com/mapstash/service/GpxService.java — GPX → GeoJSON conversion logic
- src/main/java/com/mapstash/service/FileStorageService.java — upload, checksum, disk storage
- src/main/java/com/mapstash/repository/GpxFileRepository.java and src/main/java/com/mapstash/model/GpxFile.java — persistence model
- src/main/java/com/mapstash/config/NativeRuntimeHints.java — where native image reflection hints are registered
- src/main/resources/templates/map.html and index.html — where GeoJSON is embedded and Mapbox is initialized
- src/main/resources/db/migration — Flyway migrations for DB schema
- src/main/resources/application-local.properties.template — local configuration example

Key design and conventions (what to expect when reading code)
- Server-side rendering: Data for Mapbox is embedded into templates using th:inline="javascript" and the /*[[${geoJson}]]*/ syntax — always check map.html and index.html for how data flows to client.
- File storage model: GPX files are saved in uploads/ (configurable via mapstash.upload.directory). The DB stores metadata (checksum, original name, timestamps). The entity's path is transient and derived at runtime.
- GPX parsing: JPX library (io.jenetics:jpx) is used; GpxService handles tracks, routes, waypoints and converts to GeoJSON FeatureCollection programmatically using Jackson.
- Native image support: RuntimeHintsRegistrar (src/main/java/.../NativeRuntimeHints.java) is used to register reflection hints for native images. Do not add static reflect-config.json/reachability-metadata.json — the AOT process overwrites/ignores those.

Configuration and environment
- Mapbox token (required): set MAPBOX_TOKEN env var (recommended) or use application-local.properties (copy template and edit).
  export MAPBOX_TOKEN=pk.your-token-here
- Database: PostgreSQL with PostGIS. Default DB variables (can be overridden):
  DATABASE_URL (jdbc:postgresql://localhost:5432/mapstash_db)
  DATABASE_USER (mapstash)
  DATABASE_PASSWORD (mapstash)
- Local profile: run make run or set -Dspring-boot.run.profiles=local to enable port 4200 and development-friendly settings.

Native image / GraalVM notes (important)
- Use SDKMAN-managed GraalVM JDK 25+ for native builds. Must set JAVA_HOME to GraalVM before running native profile.
- Register reflection/runtime hints in NativeRuntimeHints.java. If you see MissingReflectionRegistrationError at runtime, add the class there with MemberCategory flags and rebuild native image.
- Avoid adding META-INF/native-image JSON files — use the RuntimeHintsRegistrar approach described in the repo.

How to reproduce a native-image reflection error locally
1. Build native binary: export JAVA_HOME=/path/to/graalvm && mvn clean package -Pnative -DskipTests
2. Run the produced binary: ./target/mapstash
3. If the binary fails with MissingReflectionRegistrationError, the stack trace will indicate the missing type or method. Add the class to src/main/java/com/mapstash/config/NativeRuntimeHints.java (use MemberCategory constants appropriately) and rebuild.

APIs / Routes (high level)
- GET / — home page (Thymeleaf)
- POST /upload — upload GPX (redirect to map view)
- GET /map/{fileId} — render map page for a file
- POST /delete/{fileId} — delete file and metadata
- GET /api/gpx/{fileId} — return raw GeoJSON string (useful for automated tests or client fetch)

Testing guidance
- Unit tests use Maven test lifecycle. Run a single test class or a single test method with:
  mvn test -Dtest=ClassName
  mvn test -Dtest=ClassName#methodName
- When changing conversion logic (GpxService), add tests that load sample GPX fixtures and assert the produced GeoJSON structure (FeatureCollection, geometry types, properties).
- Integration tests that require DB should use the same database configuration; Flyway migrations will run automatically on startup.

Suggested repository improvements (applied here)
1. Quick commands cheat sheet added at top for immediate onboarding and common tasks.
2. Curl example for fetching GeoJSON added to quick commands (useful for integration testing):
   curl -s http://localhost:4200/api/gpx/{fileId}
3. Suggest adding a test-fixtures/ directory for deterministic GPX sample files used by unit tests. This file does not create the directory — ask if you want me to add a sample fixture and tests.
4. "Important files to read first" list included to speed onboarding.
5. Explicit instructions to prefer NativeRuntimeHints over static native-image JSON files and steps to reproduce and fix reflection errors locally.

Development patterns / Where to make changes
- To change how GPX is converted to GeoJSON: modify GpxService.convertToGeoJson().
- To change map rendering/styles: update templates/map.html and the embedded JavaScript (Mapbox layer definitions).
- To add a new file operation: implement in FileStorageService, expose new endpoint in GpxController, update templates as needed.
- To add reflection hints required for native image, update NativeRuntimeHints.java.

Common troubleshooting (concise)
- Blank or non-loading map: check browser console for Mapbox token errors; verify MAPBOX_TOKEN.
- Database connection errors: ensure PostgreSQL is running, check credentials and Flyway logs.
- Duplicate upload: detected via MD5 checksum; delete DB record to re-upload same content.
- Large GPX upload failures: check spring.servlet.multipart.max-file-size and disk space in uploads/.

Feature delivery / PRD workflow
- PRD.md tracks requirements with IDs. When implementing a requirement:
  1. Read PRD.md for acceptance criteria.
  2. Implement code changes.
  3. Update PRD.md status and add commit reference when marking implemented.

Notes
- This CLAUDE.md focuses on repository-specific commands, architecture, and decision points that require cross-file understanding. It avoids generic engineering best-practices and non-repo-specific advice.

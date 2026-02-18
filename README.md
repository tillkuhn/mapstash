# MapStash 🗺️

A Spring Boot application for storing and visualizing GPX (GPS Exchange Format) files on interactive maps using Mapbox GL JS.

## Features

- 📤 **Upload GPX Files** - Easy drag-and-drop file upload interface
- 🗺️ **Interactive Maps** - Beautiful map visualization powered by Mapbox GL JS
- 📊 **Track Display** - View your GPS tracks, routes, and waypoints
- 💾 **File Management** - Store and manage multiple GPX files
- 🎨 **Modern UI** - Clean, responsive interface with gradient design
- ⚡ **Server-Side Rendering** - Fast page loads with Thymeleaf templates

## Technology Stack

- **Backend**: Spring Boot 4.0.0 (Java 25)
- **Database**: PostgreSQL with Spring Data JPA
- **Build Tool**: Maven (via SDKMAN, no wrapper)
- **Template Engine**: Thymeleaf
- **Map Rendering**: Mapbox GL JS v3.1.0
- **GPX Parsing**: io.jenetics:jpx library
- **Data Format**: GeoJSON
- **CI/CD**: GitHub Actions

## Prerequisites

- Java 25 or higher
- Maven 3.6+ (installed via SDKMAN)
- PostgreSQL 12+ (for database storage)
- A Mapbox account and API token (free tier available)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd mapstash
```

### 2. Set Up PostgreSQL Database

**Create Database and User:**
```bash
# Using Makefile (if PGDATA is set)
make create-db

# Or manually with psql
createdb mapstash_db
createuser mapstash
psql -c "ALTER USER mapstash WITH PASSWORD 'mapstash';"
psql -c "GRANT ALL PRIVILEGES ON DATABASE mapstash_db TO mapstash;"
```

**Configure Database Connection:**

The application uses these environment variables (with defaults):
- `DATABASE_URL` (default: `jdbc:postgresql://localhost:5432/mapstash_db`)
- `DATABASE_USER` (default: `mapstash`)
- `DATABASE_PASSWORD` (default: `mapstash`)

Or configure in `application-local.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mapstash_db
spring.datasource.username=mapstash
spring.datasource.password=mapstash
```

> **Note**: Flyway will automatically create and migrate the database schema on first run.

### 3. Configure Mapbox Token

Get your free Mapbox token from [https://account.mapbox.com/access-tokens/](https://account.mapbox.com/access-tokens/)

Then set it in one of these ways:

**Option A: Environment Variable (Recommended)**
```bash
export MAPBOX_TOKEN=your-token-here
```

**Option B: Local Properties File (Recommended for Development)**

Copy the template and add your token:
```bash
cp src/main/resources/application-local.properties.template src/main/resources/application-local.properties
```

Edit `application-local.properties` and add your token:
```properties
server.port=4200
mapstash.mapbox.token=pk.your-actual-token-here
```

> **Note**: The local profile automatically uses port 4200 for development. This profile is activated automatically when using `make run` or `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

**Option C: Direct Configuration**

Edit `src/main/resources/application.properties` and replace the placeholder:
```properties
mapstash.mapbox.token=pk.your-actual-token-here
```

### 4. Build the Application

**Using Makefile (Recommended):**
```bash
make build              # Build with tests
make build-skip-tests   # Build without tests (faster)
```

**Using Maven directly:**
```bash
mvn clean package              # Build with tests
mvn clean package -DskipTests  # Build without tests
```

### 5. Run the Application

**Using Makefile (Recommended):**
```bash
make run
```

**Using Maven directly:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**Or run the JAR file:**
```bash
java -jar target/mapstash-0.1.0-SNAPSHOT.jar
```

### 6. Access the Application

Open your browser and navigate to:
```
http://localhost:4200
```

> **Note**: Development uses port 4200 (local profile). Production deployments default to port 8080 unless configured otherwise.

## Usage

1. **Upload a GPX File**
   - Click "Choose GPX File" on the home page
   - Select a `.gpx` file from your device
   - Click "Upload"

2. **View Your Track**
   - After upload, you'll be automatically redirected to the map view
   - Or click "View Map" next to any file in the list

3. **Interact with the Map**
   - Zoom and pan the map
   - Click on waypoints to see details
   - Click on track lines to see track names
   - Use the navigation controls in the bottom-right

4. **Manage Files**
   - View all uploaded files on the home page
   - Delete files you no longer need
   - Upload multiple files to compare

## Project Structure

```
mapstash/
├── .github/
│   └── workflows/
│       └── ci.yml                              # GitHub Actions CI workflow
├── src/
│   ├── main/
│   │   ├── java/com/mapstash/
│   │   │   ├── MapStashApplication.java      # Main application class
│   │   │   ├── controller/
│   │   │   │   └── GpxController.java        # Web controller
│   │   │   ├── service/
│   │   │   │   ├── GpxService.java           # GPX parsing & conversion
│   │   │   │   └── FileStorageService.java   # File management
│   │   │   ├── model/
│   │   │   │   └── GpxFile.java              # JPA entity
│   │   │   └── repository/
│   │   │       └── GpxFileRepository.java    # Spring Data JPA repository
│   │   └── resources/
│   │       ├── application.properties         # Configuration
│   │       ├── db/migration/                  # Flyway database migrations
│   │       ├── templates/                     # Thymeleaf templates
│   │       │   ├── index.html                # Home page
│   │       │   ├── map.html                  # Map view
│   │       │   └── error.html                # Error page
│   │       └── static/                        # Static assets
│   └── test/
│       └── java/com/mapstash/
│           └── service/
│               └── GpxServiceTest.java        # Unit tests
├── uploads/                                    # GPX file storage (created at runtime)
├── Makefile                                    # Build shortcuts
├── pom.xml                                     # Maven configuration
└── README.md
```

## Configuration

Key configuration properties in `application.properties`:

```properties
# Server port (default for production)
server.port=8080

# Database configuration
spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/mapstash_db}
spring.datasource.username=${DATABASE_USER:mapstash}
spring.datasource.password=${DATABASE_PASSWORD:mapstash}

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false

# Flyway (database migrations)
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true

# File upload limits
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# Upload directory
mapstash.upload.directory=uploads

# Mapbox token
mapstash.mapbox.token=${MAPBOX_TOKEN:your-token-here}
```

### Local Development Configuration

When using the `local` profile (`application-local.properties`):

```properties
# Development server port
server.port=4200

# Database configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/mapstash_db
spring.datasource.username=mapstash
spring.datasource.password=mapstash

# Mapbox token
mapstash.mapbox.token=pk.your-actual-token-here
```

The local profile is automatically activated when using:
- `make run`
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`

## API Endpoints

- `GET /` - Home page with file list and upload form
- `POST /upload` - Upload a GPX file
- `GET /map/{fileId}` - View map for a specific file
- `POST /delete/{fileId}` - Delete a file
- `GET /api/gpx/{fileId}` - Get GeoJSON for a file (REST API)

## Development

### Quick Start Commands

```bash
make run                # Run with local profile (port 4200)
make build              # Build JAR with tests
make build-skip-tests   # Build JAR without tests
make jar                # Alias for 'make build'
make test               # Run unit tests
make test-verbose       # Run tests with verbose output
```

### Testing

The project includes comprehensive unit tests with GitHub Actions CI/CD integration.

**Run Tests Locally:**
```bash
# Using Makefile
make test

# Or with Maven
mvn test

# Run specific test class
mvn test -Dtest=GpxServiceTest

# Run specific test method
mvn test -Dtest=GpxServiceTest#testConvertToGeoJson_WithValidGpxFile
```

**Continuous Integration:**

Every push to `main` and all pull requests automatically trigger the CI pipeline:
- ✅ Compiles the code
- ✅ Runs all unit tests
- ✅ Uploads test results as artifacts

View CI status: Check the "Actions" tab in the GitHub repository

**Test Coverage:**
- `GpxServiceTest`: Tests GPX to GeoJSON conversion and bounds calculation
- More tests coming soon for integration testing

### Hot Reload

The application includes Spring Boot DevTools for automatic restart during development:

```bash
make run
# or
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Edit any Java file and it will automatically restart. Template changes are reflected immediately.

### Building for Production

```bash
make build-skip-tests
java -jar target/mapstash-0.1.0-SNAPSHOT.jar
```

Or with Maven:
```bash
mvn clean package -DskipTests
java -jar target/mapstash-0.1.0-SNAPSHOT.jar
```

## Troubleshooting

### Database Connection Issues

- **PostgreSQL Not Running**: Check if PostgreSQL is running: `pg_isready`
- **Database Doesn't Exist**: Run `make create-db` or create manually
- **Connection Refused**: Verify database is listening on port 5432
- **Authentication Failed**: Check username/password in configuration
- **Flyway Migration Errors**: Check logs for schema migration issues

### Map Not Loading

- **Check Mapbox Token**: Ensure your token is correctly set and valid
- **Browser Console**: Open browser dev tools to check for errors
- **Network Tab**: Verify Mapbox API requests are succeeding

### File Upload Fails

- **File Size**: Ensure file is under 50MB
- **File Type**: Only `.gpx` files are accepted
- **Permissions**: Check write permissions on the `uploads/` directory
- **Database Error**: Check database connection and Flyway migrations

### Port Already in Use

For development, the local profile uses port 4200. For production, the default is 8080.

Change the port in `application.properties` (production) or `application-local.properties` (development):
```properties
server.port=8081
```

### Tests Failing

- **Database Required**: Some tests may require a running PostgreSQL instance
- **Check Logs**: Run tests with verbose output: `make test-verbose`
- **Clean Build**: Try `mvn clean test` to ensure no stale artifacts

## Future Enhancements

- ✅ Database storage for metadata (Implemented - REQ-008)
- ✅ Continuous Integration with GitHub Actions (Implemented)
- 📈 Track statistics (distance, elevation, duration)
- 🔍 Search and filter capabilities
- 🎯 Multiple track comparison
- 📱 Mobile-responsive design improvements
- 🌙 Dark mode support
- 🔐 User authentication
- 🧪 Integration tests with Testcontainers

## License

This project is open source and available under the [MIT License](LICENSE).

## Acknowledgments

- [Mapbox](https://www.mapbox.com/) for the amazing mapping platform
- [JPX Library](https://github.com/jenetics/jpx) for GPX parsing
- [Spring Boot](https://spring.io/projects/spring-boot) for the robust framework
- [Thymeleaf](https://www.thymeleaf.org/) for server-side templating

---

Made with ❤️ and ☕ for outdoor enthusiasts and GPS track lovers!

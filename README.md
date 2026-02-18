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

- **Backend**: Spring Boot 3.2.2 (Java 21)
- **Build Tool**: Maven
- **Template Engine**: Thymeleaf
- **Map Rendering**: Mapbox GL JS v3.1.0
- **GPX Parsing**: io.jenetics:jpx library
- **Data Format**: GeoJSON

## Prerequisites

- Java 21 or higher
- Maven 3.6+
- A Mapbox account and API token (free tier available)

## Getting Started

### 1. Clone the Repository

```bash
git clone <repository-url>
cd mapstash
```

### 2. Configure Mapbox Token

Get your free Mapbox token from [https://account.mapbox.com/access-tokens/](https://account.mapbox.com/access-tokens/)

Then set it in one of these ways:

**Option A: Environment Variable (Recommended)**
```bash
export MAPBOX_TOKEN=your-token-here
```

**Option B: Application Properties**

Copy the template and add your token:
```bash
cp src/main/resources/application-local.properties.template src/main/resources/application-local.properties
```

Edit `application-local.properties` and add your token:
```properties
mapstash.mapbox.token=pk.your-actual-token-here
```

**Option C: Direct Configuration**

Edit `src/main/resources/application.properties` and replace the placeholder:
```properties
mapstash.mapbox.token=pk.your-actual-token-here
```

### 3. Build the Application

```bash
./mvnw clean package
```

### 4. Run the Application

```bash
./mvnw spring-boot:run
```

Or run the JAR file:
```bash
java -jar target/mapstash-0.1.0-SNAPSHOT.jar
```

### 5. Access the Application

Open your browser and navigate to:
```
http://localhost:8080
```

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
├── src/
│   ├── main/
│   │   ├── java/com/mapstash/
│   │   │   ├── MapStashApplication.java      # Main application class
│   │   │   ├── controller/
│   │   │   │   └── GpxController.java        # Web controller
│   │   │   ├── service/
│   │   │   │   ├── GpxService.java           # GPX parsing & conversion
│   │   │   │   └── FileStorageService.java   # File management
│   │   │   └── model/
│   │   │       └── GpxFile.java              # Data model
│   │   └── resources/
│   │       ├── application.properties         # Configuration
│   │       ├── templates/                     # Thymeleaf templates
│   │       │   ├── index.html                # Home page
│   │       │   ├── map.html                  # Map view
│   │       │   └── error.html                # Error page
│   │       └── static/                        # Static assets
│   └── test/
├── uploads/                                    # GPX file storage (created at runtime)
├── pom.xml                                     # Maven configuration
└── README.md
```

## Configuration

Key configuration properties in `application.properties`:

```properties
# Server port
server.port=8080

# File upload limits
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# Upload directory
mapstash.upload.directory=uploads

# Mapbox token
mapstash.mapbox.token=${MAPBOX_TOKEN:your-token-here}
```

## API Endpoints

- `GET /` - Home page with file list and upload form
- `POST /upload` - Upload a GPX file
- `GET /map/{fileId}` - View map for a specific file
- `POST /delete/{fileId}` - Delete a file
- `GET /api/gpx/{fileId}` - Get GeoJSON for a file (REST API)

## Development

### Hot Reload

The application includes Spring Boot DevTools for automatic restart during development:

```bash
./mvnw spring-boot:run
```

Edit any Java file and it will automatically restart. Template changes are reflected immediately.

### Building for Production

```bash
./mvnw clean package -DskipTests
java -jar target/mapstash-0.1.0-SNAPSHOT.jar
```

## Troubleshooting

### Map Not Loading

- **Check Mapbox Token**: Ensure your token is correctly set and valid
- **Browser Console**: Open browser dev tools to check for errors
- **Network Tab**: Verify Mapbox API requests are succeeding

### File Upload Fails

- **File Size**: Ensure file is under 50MB
- **File Type**: Only `.gpx` files are accepted
- **Permissions**: Check write permissions on the `uploads/` directory

### Port Already in Use

Change the port in `application.properties`:
```properties
server.port=8081
```

## Future Enhancements

- 🗄️ Database storage for metadata
- 📈 Track statistics (distance, elevation, duration)
- 🔍 Search and filter capabilities
- 🎯 Multiple track comparison
- 📱 Mobile-responsive design improvements
- 🌙 Dark mode support
- 🔐 User authentication

## License

This project is open source and available under the [MIT License](LICENSE).

## Acknowledgments

- [Mapbox](https://www.mapbox.com/) for the amazing mapping platform
- [JPX Library](https://github.com/jenetics/jpx) for GPX parsing
- [Spring Boot](https://spring.io/projects/spring-boot) for the robust framework
- [Thymeleaf](https://www.thymeleaf.org/) for server-side templating

---

Made with ❤️ and ☕ for outdoor enthusiasts and GPS track lovers!

package com.mapstash.service;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

class GpxServiceTest {

    private GpxService gpxService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        gpxService = new GpxService(objectMapper);
    }

    @Test
    void testConvertToGeoJson_WithValidGpxFile() throws IOException {
        // Create a minimal valid GPX file
        String gpxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="MapStash Test"
                     xmlns="http://www.topografix.com/GPX/1/1"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
                    <trk>
                        <name>Test Track</name>
                        <trkseg>
                            <trkpt lat="52.5200" lon="13.4050">
                                <ele>34</ele>
                            </trkpt>
                            <trkpt lat="52.5210" lon="13.4060">
                                <ele>35</ele>
                            </trkpt>
                        </trkseg>
                    </trk>
                </gpx>
                """;

        Path gpxFile = tempDir.resolve("test.gpx");
        Files.writeString(gpxFile, gpxContent);

        // Test conversion
        String geoJson = gpxService.convertToGeoJson(gpxFile);

        // Verify result
        assertNotNull(geoJson, "GeoJSON should not be null");
        assertTrue(geoJson.contains("FeatureCollection"), "Should contain FeatureCollection type");
        assertTrue(geoJson.contains("LineString"), "Should contain LineString geometry");
        assertTrue(geoJson.contains("Test Track"), "Should contain track name");
        assertTrue(geoJson.contains("52.52"), "Should contain latitude coordinate");
        assertTrue(geoJson.contains("13.405"), "Should contain longitude coordinate");
    }

    @Test
    void testCalculateBounds_WithValidGpxFile() throws IOException {
        // Create a minimal valid GPX file
        String gpxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="MapStash Test"
                     xmlns="http://www.topografix.com/GPX/1/1">
                    <trk>
                        <trkseg>
                            <trkpt lat="52.5200" lon="13.4050"/>
                            <trkpt lat="52.5300" lon="13.4150"/>
                        </trkseg>
                    </trk>
                </gpx>
                """;

        Path gpxFile = tempDir.resolve("test.gpx");
        Files.writeString(gpxFile, gpxContent);

        // Test bounds calculation
        double[] bounds = gpxService.calculateBounds(gpxFile);

        // Verify result
        assertNotNull(bounds, "Bounds should not be null");
        assertEquals(4, bounds.length, "Bounds array should have 4 elements");
        assertEquals(13.4050, bounds[0], 0.0001, "Min longitude should be correct");
        assertEquals(52.5200, bounds[1], 0.0001, "Min latitude should be correct");
        assertEquals(13.4150, bounds[2], 0.0001, "Max longitude should be correct");
        assertEquals(52.5300, bounds[3], 0.0001, "Max latitude should be correct");
    }

    @Test
    void testConvertToGeoJson_WithInvalidFile_ThrowsException() {
        Path nonExistentFile = tempDir.resolve("nonexistent.gpx");

        assertThrows(IOException.class, () -> {
            gpxService.convertToGeoJson(nonExistentFile);
        }, "Should throw IOException for non-existent file");
    }

    @Test
    void testExtractName_WithMetadataName() throws IOException {
        // Create GPX file with metadata name
        String gpxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="MapStash Test"
                     xmlns="http://www.topografix.com/GPX/1/1">
                    <metadata>
                        <name>My Amazing Hike</name>
                    </metadata>
                    <trk>
                        <trkseg>
                            <trkpt lat="52.5200" lon="13.4050"/>
                        </trkseg>
                    </trk>
                </gpx>
                """;

        Path gpxFile = tempDir.resolve("test.gpx");
        Files.writeString(gpxFile, gpxContent);

        String name = gpxService.extractName(gpxFile);
        assertEquals("My Amazing Hike", name, "Should extract metadata name");
    }

    @Test
    void testExtractName_WithTrackName() throws IOException {
        // Create GPX file with track name but no metadata
        String gpxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="MapStash Test"
                     xmlns="http://www.topografix.com/GPX/1/1">
                    <trk>
                        <name>Mountain Trail</name>
                        <trkseg>
                            <trkpt lat="52.5200" lon="13.4050"/>
                        </trkseg>
                    </trk>
                </gpx>
                """;

        Path gpxFile = tempDir.resolve("test.gpx");
        Files.writeString(gpxFile, gpxContent);

        String name = gpxService.extractName(gpxFile);
        assertEquals("Mountain Trail", name, "Should extract track name");
    }

    @Test
    void testExtractName_WithNoNames() throws IOException {
        // Create GPX file without any names
        String gpxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="MapStash Test"
                     xmlns="http://www.topografix.com/GPX/1/1">
                    <trk>
                        <trkseg>
                            <trkpt lat="52.5200" lon="13.4050"/>
                        </trkseg>
                    </trk>
                </gpx>
                """;

        Path gpxFile = tempDir.resolve("test.gpx");
        Files.writeString(gpxFile, gpxContent);

        String name = gpxService.extractName(gpxFile);
        assertNull(name, "Should return null when no names are found");
    }

    @Test
    void testConvertToGeoJson_WithMultiSegmentFixture() throws Exception {
        Path gpxFile = pathForResource("/test-fixtures/multi-segment.gpx");
        String geoJson = gpxService.convertToGeoJson(gpxFile);

        assertNotNull(geoJson, "GeoJSON should not be null");
        assertTrue(geoJson.contains("FeatureCollection"), "Should contain FeatureCollection type");
        assertTrue(geoJson.contains("LineString"), "Should contain LineString geometry");
        assertTrue(geoJson.contains("MultiSegment Track"), "Should contain the multi-segment track name");
    }

    @Test
    void testConvertToGeoJson_WithMultiTrackFixture() throws Exception {
        Path gpxFile = pathForResource("/test-fixtures/multi-track.gpx");
        String geoJson = gpxService.convertToGeoJson(gpxFile);

        assertNotNull(geoJson, "GeoJSON should not be null");
        assertTrue(geoJson.contains("FeatureCollection"), "Should contain FeatureCollection type");
        assertTrue(geoJson.contains("Track One"), "Should contain first track name");
        assertTrue(geoJson.contains("Track Two"), "Should contain second track name");

        int lineStringCount = countOccurrences(geoJson, "LineString");
        assertTrue(lineStringCount >= 2, "Should contain at least two LineString geometries for multiple tracks");
    }

    // Helper to load test resource as Path
    private Path pathForResource(String resourcePath) throws URISyntaxException {
        URL resource = getClass().getResource(resourcePath);
        assertNotNull(resource, "Test resource not found: " + resourcePath);
        return Paths.get(resource.toURI());
    }

    // Simple occurrence counter
    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}

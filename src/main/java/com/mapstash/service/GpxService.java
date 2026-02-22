package com.mapstash.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;


@Service
@RequiredArgsConstructor
@Slf4j
public class GpxService {

    /**
     * Extract start point (first track coordinate) as a JTS Point (SRID 4326)
     * Defaults to POINT(0 0) if track/segment/point not found.
     */
    public Point extractStartPoint(Path gpxFilePath) throws IOException {
        // Use JPX Path reader directly (available in the library)
        GPX gpx = GPX.read(gpxFilePath);
        return gpx.getTracks().stream()
            .flatMap(track -> track.getSegments().stream())
            .flatMap(segment -> segment.getPoints().stream())
            .findFirst()
            .map(point -> {
                GeometryFactory gf = new GeometryFactory();
                Point pt = gf.createPoint(new Coordinate(point.getLongitude().doubleValue(), point.getLatitude().doubleValue()));
                pt.setSRID(4326);
                return pt;
            })
            .orElseGet(() -> {
                GeometryFactory gf = new GeometryFactory();
                Point pt = gf.createPoint(new Coordinate(0, 0));
                pt.setSRID(4326);
                return pt;
            });
    }

    /**
     * Same as extractStartPoint but reads from an InputStream (does not require a filesystem path)
     */
    public Point extractStartPoint(InputStream in) throws IOException {
        GPX gpx = readGpxFromStream(in);
        return gpx.getTracks().stream()
            .flatMap(track -> track.getSegments().stream())
            .flatMap(segment -> segment.getPoints().stream())
            .findFirst()
            .map(point -> {
                GeometryFactory gf = new GeometryFactory();
                Point pt = gf.createPoint(new Coordinate(point.getLongitude().doubleValue(), point.getLatitude().doubleValue()));
                pt.setSRID(4326);
                return pt;
            })
            .orElseGet(() -> {
                GeometryFactory gf = new GeometryFactory();
                Point pt = gf.createPoint(new Coordinate(0, 0));
                pt.setSRID(4326);
                return pt;
            });
    }


    private final ObjectMapper objectMapper;

    /**
     * Parse a GPX file and convert it to GeoJSON format for Mapbox GL JS
     *
     * @param gpxFilePath Path to the GPX file
     * @return GeoJSON string representation
     * @throws IOException if file cannot be read or parsed
     */
    public String convertToGeoJson(Path gpxFilePath) throws IOException {
        log.info("Converting GPX file to GeoJSON: {}", gpxFilePath);
        GPX gpx = GPX.read(gpxFilePath);

        ObjectNode featureCollection = objectMapper.createObjectNode();
        featureCollection.put("type", "FeatureCollection");

        ArrayNode features = featureCollection.putArray("features");

        // Process tracks
        gpx.getTracks().forEach(track -> {
            ObjectNode feature = createLineStringFeature(track);
            features.add(feature);
        });

        // Process routes
        gpx.getRoutes().forEach(route -> {
            ObjectNode feature = objectMapper.createObjectNode();
            feature.put("type", "Feature");

            ObjectNode geometry = feature.putObject("geometry");
            geometry.put("type", "LineString");

            ArrayNode coordinates = geometry.putArray("coordinates");
            route.getPoints().forEach(point -> {
                ArrayNode coord = coordinates.addArray();
                coord.add(point.getLongitude().doubleValue());
                coord.add(point.getLatitude().doubleValue());
                point.getElevation().ifPresent(elevation -> coord.add(elevation.doubleValue()));
            });

            ObjectNode properties = feature.putObject("properties");
            route.getName().ifPresent(name -> properties.put("name", name));
            properties.put("type", "route");

            features.add(feature);
        });

        // Process waypoints
        gpx.getWayPoints().forEach(waypoint -> {
            ObjectNode feature = objectMapper.createObjectNode();
            feature.put("type", "Feature");

            ObjectNode geometry = feature.putObject("geometry");
            geometry.put("type", "Point");

            ArrayNode coordinates = geometry.putArray("coordinates");
            coordinates.add(waypoint.getLongitude().doubleValue());
            coordinates.add(waypoint.getLatitude().doubleValue());
            waypoint.getElevation().ifPresent(elevation -> coordinates.add(elevation.doubleValue()));

            ObjectNode properties = feature.putObject("properties");
            waypoint.getName().ifPresent(name -> properties.put("name", name));
            waypoint.getDescription().ifPresent(desc -> properties.put("description", desc));
            properties.put("type", "waypoint");

            features.add(feature);
        });

        return objectMapper.writeValueAsString(featureCollection);
    }

    /**
     * Same as convertToGeoJson but reads from InputStream
     */
    public String convertToGeoJson(InputStream in) throws IOException {
        log.info("Converting GPX stream to GeoJSON");

        GPX gpx = readGpxFromStream(in);

        ObjectNode featureCollection = objectMapper.createObjectNode();
        featureCollection.put("type", "FeatureCollection");

        ArrayNode features = featureCollection.putArray("features");

        // Process tracks
        gpx.getTracks().forEach(track -> {
            ObjectNode feature = createLineStringFeature(track);
            features.add(feature);
        });

        // Process routes
        gpx.getRoutes().forEach(route -> {
            ObjectNode feature = objectMapper.createObjectNode();
            feature.put("type", "Feature");

            ObjectNode geometry = feature.putObject("geometry");
            geometry.put("type", "LineString");

            ArrayNode coordinates = geometry.putArray("coordinates");
            route.getPoints().forEach(point -> {
                ArrayNode coord = coordinates.addArray();
                coord.add(point.getLongitude().doubleValue());
                coord.add(point.getLatitude().doubleValue());
                point.getElevation().ifPresent(elevation -> coord.add(elevation.doubleValue()));
            });

            ObjectNode properties = feature.putObject("properties");
            route.getName().ifPresent(name -> properties.put("name", name));
            properties.put("type", "route");

            features.add(feature);
        });

        // Process waypoints
        gpx.getWayPoints().forEach(waypoint -> {
            ObjectNode feature = objectMapper.createObjectNode();
            feature.put("type", "Feature");

            ObjectNode geometry = feature.putObject("geometry");
            geometry.put("type", "Point");

            ArrayNode coordinates = geometry.putArray("coordinates");
            coordinates.add(waypoint.getLongitude().doubleValue());
            coordinates.add(waypoint.getLatitude().doubleValue());
            waypoint.getElevation().ifPresent(elevation -> coordinates.add(elevation.doubleValue()));

            ObjectNode properties = feature.putObject("properties");
            waypoint.getName().ifPresent(name -> properties.put("name", name));
            waypoint.getDescription().ifPresent(desc -> properties.put("description", desc));
            properties.put("type", "waypoint");

            features.add(feature);
        });

        return objectMapper.writeValueAsString(featureCollection);
    }

    /**
     * Calculate bounding box for a GPX file (for map centering)
     *
     * @param gpxFilePath Path to the GPX file
     * @return Array of [minLon, minLat, maxLon, maxLat]
     * @throws IOException if file cannot be read or parsed
     */
    public double[] calculateBounds(Path gpxFilePath) throws IOException {
        GPX gpx = GPX.read(gpxFilePath);

        Stream<WayPoint> allPoints = Stream.concat(
                Stream.concat(
                        gpx.getTracks().stream()
                                .flatMap(track -> track.getSegments().stream())
                                .flatMap(segment -> segment.getPoints().stream()),
                        gpx.getRoutes().stream()
                                .flatMap(route -> route.getPoints().stream())
                ),
                gpx.getWayPoints().stream()
        );

        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        allPoints.forEach(point -> {
            double lon = point.getLongitude().doubleValue();
            double lat = point.getLatitude().doubleValue();

            bounds[0] = Math.min(bounds[0], lon); // minLon
            bounds[1] = Math.min(bounds[1], lat); // minLat
            bounds[2] = Math.max(bounds[2], lon); // maxLon
            bounds[3] = Math.max(bounds[3], lat); // maxLat
        });

        return bounds;
    }

    /**
     * Calculate bounds from InputStream
     */
    public double[] calculateBounds(InputStream in) throws IOException {
        GPX gpx = readGpxFromStream(in);

        Stream<WayPoint> allPoints = Stream.concat(
                Stream.concat(
                        gpx.getTracks().stream()
                                .flatMap(track -> track.getSegments().stream())
                                .flatMap(segment -> segment.getPoints().stream()),
                        gpx.getRoutes().stream()
                                .flatMap(route -> route.getPoints().stream())
                ),
                gpx.getWayPoints().stream()
        );

        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        allPoints.forEach(point -> {
            double lon = point.getLongitude().doubleValue();
            double lat = point.getLatitude().doubleValue();

            bounds[0] = Math.min(bounds[0], lon); // minLon
            bounds[1] = Math.min(bounds[1], lat); // minLat
            bounds[2] = Math.max(bounds[2], lon); // maxLon
            bounds[3] = Math.max(bounds[3], lat); // maxLat
        });

        return bounds;
    }

    private ObjectNode createLineStringFeature(Track track) {
        ObjectNode feature = objectMapper.createObjectNode();
        feature.put("type", "Feature");

        ObjectNode geometry = feature.putObject("geometry");

        // If track has multiple segments, create MultiLineString, otherwise LineString
        if (track.getSegments().size() > 1) {
            geometry.put("type", "MultiLineString");
            ArrayNode coordinatesArray = geometry.putArray("coordinates");

            track.getSegments().forEach(segment -> {
                ArrayNode segmentCoords = coordinatesArray.addArray();
                addSegmentCoordinates(segment, segmentCoords);
            });
        } else {
            geometry.put("type", "LineString");
            ArrayNode coordinates = geometry.putArray("coordinates");

            track.getSegments().stream()
                    .findFirst()
                    .ifPresent(segment -> addSegmentCoordinates(segment, coordinates));
        }

        ObjectNode properties = feature.putObject("properties");
        track.getName().ifPresent(name -> properties.put("name", name));
        properties.put("type", "track");

        return feature;
    }

    private void addSegmentCoordinates(TrackSegment segment, ArrayNode coordinates) {
        segment.getPoints().forEach(point -> {
            ArrayNode coord = coordinates.addArray();
            coord.add(point.getLongitude().doubleValue());
            coord.add(point.getLatitude().doubleValue());
            point.getElevation().ifPresent(elevation -> coord.add(elevation.doubleValue()));
        });
    }

    /**
     * Extract name from GPX metadata
     * Checks metadata first, then first track/route name, or returns null if not found
     *
     * @param gpxFilePath Path to the GPX file
     * @return Name from GPX metadata or null
     * @throws IOException if file cannot be read or parsed
     */
    public String extractName(Path gpxFilePath) throws IOException {
        try (InputStream in = Files.newInputStream(gpxFilePath)) {
            return extractName(in);
        }
    }

    /**
     * Extract name from InputStream
     */
    public String extractName(InputStream in) throws IOException {
        GPX gpx = readGpxFromStream(in);

        if (gpx.getMetadata().isPresent() && gpx.getMetadata().get().getName().isPresent()) {
            return gpx.getMetadata().get().getName().get();
        }

        if (!gpx.getTracks().isEmpty() && gpx.getTracks().getFirst().getName().isPresent()) {
            return gpx.getTracks().getFirst().getName().get();
        }

        if (!gpx.getRoutes().isEmpty() && gpx.getRoutes().getFirst().getName().isPresent()) {
            return gpx.getRoutes().getFirst().getName().get();
        }

        return null;
    }

    /**
     * Helper: read GPX from an InputStream by writing to a temp file and using GPX.read(Path).
     */
    private GPX readGpxFromStream(InputStream in) throws IOException {
        Path tmp = Files.createTempFile("mapstash-gpx-", ".gpx");
        try (java.io.OutputStream out = Files.newOutputStream(tmp)) {
            in.transferTo(out);
        }
        try {
            return GPX.read(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

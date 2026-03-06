package com.mapstash.controller;

import com.mapstash.model.GpxFile;
import com.mapstash.repository.GpxFileRepository;
import com.mapstash.service.FileStorageService;
import com.mapstash.service.GpxService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GpxController {

  private final FileStorageService fileStorageService;
  private final GpxService gpxService;
  private final GpxFileRepository gpxFileRepository;

  @Value("${mapstash.mapbox.token}")
  private String mapboxToken;

  /** Home page - shows upload form and list of files */
  @GetMapping("/")
  public String index(Model model) {
    List<GpxFile> files = fileStorageService.listFiles();
    model.addAttribute("files", files);
    return "index";
  }

  /** Handle file upload */
  @PostMapping("/upload")
  public String uploadFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "description", required = false) String description,
      RedirectAttributes redirectAttributes) {
    try {
      if (file.isEmpty()) {
        redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
        return "redirect:/";
      }

      GpxFile gpxFile = fileStorageService.storeFile(file, description);
      log.info("File uploaded successfully: {}", gpxFile.getOriginalFilename());

      redirectAttributes.addFlashAttribute(
          "message", "File uploaded successfully: " + gpxFile.getOriginalFilename());
      return "redirect:/map/" + gpxFile.getId();

    } catch (Exception e) {
      log.error("Error uploading file", e);
      redirectAttributes.addFlashAttribute("error", "Failed to upload file: " + e.getMessage());
      return "redirect:/";
    }
  }

  /** Display map view for a specific GPX file */
  @GetMapping("/map/{fileId}")
  public String viewMap(@PathVariable String fileId, Model model) {
    try {
      // Fetch GpxFile from database to get the name
      GpxFile gpxFile =
          gpxFileRepository
              .findById(fileId)
              .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

      // Read content from DB (gpx_contents) via FileStorageService
      String geoJson = fileStorageService.getGeoJsonForFile(fileId);
      double[] bounds = fileStorageService.getBoundsForFile(fileId);

      model.addAttribute("fileId", fileId);
      model.addAttribute("filename", gpxFile.getName()); // Use name instead of filename
      model.addAttribute("geoJson", geoJson);
      model.addAttribute("bounds", bounds);
      model.addAttribute("mapboxToken", mapboxToken);

      return "map";

    } catch (Exception e) {
      log.error("Error loading map for file: {}", fileId, e);
      model.addAttribute("error", "Error loading map: " + e.getMessage());
      return "error";
    }
  }

  /** Delete a file */
  @PostMapping("/delete/{fileId}")
  public String deleteFile(@PathVariable String fileId, RedirectAttributes redirectAttributes) {
    try {
      fileStorageService.deleteFile(fileId);
      redirectAttributes.addFlashAttribute("message", "File deleted successfully");
    } catch (IOException e) {
      log.error("Error deleting file: {}", fileId, e);
      redirectAttributes.addFlashAttribute("error", "Failed to delete file: " + e.getMessage());
    }
    return "redirect:/";
  }

  /** API endpoint to get GeoJSON for a file */
  @GetMapping("/api/gpx/{fileId}")
  @ResponseBody
  public String getGeoJson(@PathVariable String fileId) throws IOException {
    // Read content from DB via FileStorageService
    return fileStorageService.getGeoJsonForFile(fileId);
  }

  // Overview map of all start points
  @GetMapping("/overview")
  public String overview(
      @RequestParam(value = "filter", required = false) String filter, Model model) {
    com.fasterxml.jackson.databind.ObjectMapper objectMapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
    com.fasterxml.jackson.databind.node.ObjectNode featureCollection =
        objectMapper.createObjectNode();
    featureCollection.put("type", "FeatureCollection");
    com.fasterxml.jackson.databind.node.ArrayNode features = featureCollection.putArray("features");
    java.util.DoubleSummaryStatistics lonStats = new java.util.DoubleSummaryStatistics();
    java.util.DoubleSummaryStatistics latStats = new java.util.DoubleSummaryStatistics();

    // Case-insensitive filter for partial name matching
    String filterLowerCase = (filter != null && !filter.trim().isEmpty())
        ? filter.trim().toLowerCase()
        : null;

    for (GpxFile file : gpxFileRepository.findAll()) {
      // Apply name filter if provided
      if (filterLowerCase != null && file.getName() != null) {
        if (!file.getName().toLowerCase().contains(filterLowerCase)) {
          continue; // Skip this file if name doesn't match filter
        }
      }

      org.locationtech.jts.geom.Point pt = file.getStartPoint();
      if (pt == null) continue;
      double lon = pt.getX();
      double lat = pt.getY();
      if (lon == 0.0 && lat == 0.0) continue;
      lonStats.accept(lon);
      latStats.accept(lat);
      com.fasterxml.jackson.databind.node.ObjectNode feature = objectMapper.createObjectNode();
      feature.put("type", "Feature");
      com.fasterxml.jackson.databind.node.ObjectNode geometry = feature.putObject("geometry");
      geometry.put("type", "Point");
      com.fasterxml.jackson.databind.node.ArrayNode coordinates = geometry.putArray("coordinates");
      coordinates.add(lon);
      coordinates.add(lat);
      com.fasterxml.jackson.databind.node.ObjectNode properties = feature.putObject("properties");
      properties.put("fileId", file.getId());
      properties.put("name", file.getName());
      if (file.getDescription() != null && !file.getDescription().isEmpty())
        properties.put("description", file.getDescription());
      features.add(feature);
    }
    double[] bounds =
        (lonStats.getCount() > 0 && latStats.getCount() > 0)
            ? new double[] {
              lonStats.getMin(), latStats.getMin(), lonStats.getMax(), latStats.getMax()
            }
            : new double[] {0, 0, 0, 0};
    String geoJsonString = featureCollection.toString();
    boolean hasGeoFeatures = features.size() > 0;
    model.addAttribute("geoJson", geoJsonString);
    model.addAttribute("hasGeoFeatures", hasGeoFeatures);
    model.addAttribute("tourCount", features.size());
    model.addAttribute("bounds", bounds);
    model.addAttribute("mapboxToken", mapboxToken);
    model.addAttribute("filter", filter != null ? filter : "");
    return "overview";
  }
}

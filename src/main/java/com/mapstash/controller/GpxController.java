package com.mapstash.controller;

import com.mapstash.model.GpxFile;
import com.mapstash.service.FileStorageService;
import com.mapstash.service.GpxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GpxController {

    private final FileStorageService fileStorageService;
    private final GpxService gpxService;

    @Value("${mapstash.mapbox.token}")
    private String mapboxToken;

    /**
     * Home page - shows upload form and list of files
     */
    @GetMapping("/")
    public String index(Model model) {
        List<GpxFile> files = fileStorageService.listFiles();
        model.addAttribute("files", files);
        return "index";
    }

    /**
     * Handle file upload
     */
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "description", required = false) String description,
                             RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/";
            }

            GpxFile gpxFile = fileStorageService.storeFile(file, description);
            log.info("File uploaded successfully: {}", gpxFile.getOriginalFilename());

            redirectAttributes.addFlashAttribute("message",
                    "File uploaded successfully: " + gpxFile.getOriginalFilename());
            return "redirect:/map/" + gpxFile.getId();

        } catch (Exception e) {
            log.error("Error uploading file", e);
            redirectAttributes.addFlashAttribute("error",
                    "Failed to upload file: " + e.getMessage());
            return "redirect:/";
        }
    }

    /**
     * Display map view for a specific GPX file
     */
    @GetMapping("/map/{fileId}")
    public String viewMap(@PathVariable String fileId, Model model) {
        try {
            Path gpxFilePath = fileStorageService.getFilePath(fileId);

            if (!Files.exists(gpxFilePath)) {
                model.addAttribute("error", "File not found: " + fileId);
                return "error";
            }

            String geoJson = gpxService.convertToGeoJson(gpxFilePath);
            double[] bounds = gpxService.calculateBounds(gpxFilePath);

            model.addAttribute("fileId", fileId);
            model.addAttribute("filename", gpxFilePath.getFileName().toString());
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

    /**
     * Delete a file
     */
    @PostMapping("/delete/{fileId}")
    public String deleteFile(@PathVariable String fileId,
                             RedirectAttributes redirectAttributes) {
        try {
            fileStorageService.deleteFile(fileId);
            redirectAttributes.addFlashAttribute("message", "File deleted successfully");
        } catch (IOException e) {
            log.error("Error deleting file: {}", fileId, e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete file: " + e.getMessage());
        }
        return "redirect:/";
    }

    /**
     * API endpoint to get GeoJSON for a file
     */
    @GetMapping("/api/gpx/{fileId}")
    @ResponseBody
    public String getGeoJson(@PathVariable String fileId) throws IOException {
        Path gpxFilePath = fileStorageService.getFilePath(fileId);

        if (!Files.exists(gpxFilePath)) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }

        return gpxService.convertToGeoJson(gpxFilePath);
    }
}

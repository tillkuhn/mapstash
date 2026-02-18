package com.mapstash.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GpxFile {
    private String id;
    private String filename;
    private String originalFilename;
    private LocalDateTime uploadDate;
    private long fileSize;
    private String path;
}

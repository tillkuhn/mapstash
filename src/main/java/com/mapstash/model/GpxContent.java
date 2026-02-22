package com.mapstash.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;

@Entity
@Table(name = "gpx_contents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GpxContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gpx_file_id", nullable = false, unique = true, length = 36)
    private String gpxFileId;

    @Lob
    @Column(name = "gpx_content", columnDefinition = "text", nullable = false)
    private String gpxContent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}

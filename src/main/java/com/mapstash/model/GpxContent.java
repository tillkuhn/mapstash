package com.mapstash.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "gpx_contents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class GpxContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gpx_file_id", nullable = false, unique = true, length = 36)
    private String gpxFileId;

    //@Lob // https://stackoverflow.com/questions/53377735/postgresql-values-of-column-type-text-are-shown-as-numbers
    @Column(name = "gpx_content", columnDefinition = "text", nullable = false)
    private String gpxContent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

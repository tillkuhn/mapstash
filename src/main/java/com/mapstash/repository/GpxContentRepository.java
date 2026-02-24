package com.mapstash.repository;

import com.mapstash.model.GpxContent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GpxContentRepository extends JpaRepository<GpxContent, Long> {
  Optional<GpxContent> findByGpxFileId(String gpxFileId);
}

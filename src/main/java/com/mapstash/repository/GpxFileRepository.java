package com.mapstash.repository;

import com.mapstash.model.GpxFile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GpxFileRepository extends JpaRepository<GpxFile, String> {
  Optional<GpxFile> findByChecksum(String checksum);
}

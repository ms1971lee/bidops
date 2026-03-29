package com.bidops.domain.artifact.repository;

import com.bidops.domain.artifact.entity.ArtifactVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersion, String> {

    List<ArtifactVersion> findByArtifactIdOrderByVersionDesc(String artifactId);

    long countByArtifactId(String artifactId);
}

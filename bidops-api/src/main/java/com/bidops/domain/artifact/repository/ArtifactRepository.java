package com.bidops.domain.artifact.repository;

import com.bidops.domain.artifact.entity.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<Artifact, String> {

    List<Artifact> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(String projectId);

    Optional<Artifact> findByIdAndProjectIdAndDeletedFalse(String id, String projectId);
}

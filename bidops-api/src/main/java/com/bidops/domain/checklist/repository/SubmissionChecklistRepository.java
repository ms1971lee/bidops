package com.bidops.domain.checklist.repository;

import com.bidops.domain.checklist.entity.SubmissionChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubmissionChecklistRepository extends JpaRepository<SubmissionChecklist, String> {

    List<SubmissionChecklist> findByProjectIdOrderByCreatedAtDesc(String projectId);

    Optional<SubmissionChecklist> findByIdAndProjectId(String id, String projectId);
}

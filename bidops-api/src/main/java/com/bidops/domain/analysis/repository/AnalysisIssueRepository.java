package com.bidops.domain.analysis.repository;

import com.bidops.domain.analysis.entity.AnalysisIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisIssueRepository extends JpaRepository<AnalysisIssue, String> {

    List<AnalysisIssue> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<AnalysisIssue> findByAnalysisJobId(String analysisJobId);

    List<AnalysisIssue> findByRequirementId(String requirementId);

    long countByProjectIdAndResolutionStatus(String projectId, String resolutionStatus);
}

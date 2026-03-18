package com.bidops.domain.analysis.repository;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

    /**
     * GET /projects/{projectId}/analysis-jobs
     * job_type, status 필터 optional
     */
    @Query("""
            SELECT j FROM AnalysisJob j
            WHERE j.projectId = :projectId
              AND (:jobType IS NULL OR j.jobType = :jobType)
              AND (:status  IS NULL OR j.status  = :status)
            ORDER BY j.createdAt DESC
            """)
    List<AnalysisJob> findByProjectId(
            @Param("projectId") String projectId,
            @Param("jobType")   AnalysisJobType jobType,
            @Param("status")    AnalysisJobStatus status);

    Optional<AnalysisJob> findByIdAndProjectId(String id, String projectId);
}

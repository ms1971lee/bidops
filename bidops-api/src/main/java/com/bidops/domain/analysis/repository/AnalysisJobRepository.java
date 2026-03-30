package com.bidops.domain.analysis.repository;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /** 동일 requirement에 대해 PENDING 또는 RUNNING 상태의 재분석 Job 존재 여부 */
    @Query("""
            SELECT j FROM AnalysisJob j
            WHERE j.targetRequirementId = :requirementId
              AND j.jobType = 'REQUIREMENT_INSIGHT_REANALYZE'
              AND j.status IN ('PENDING', 'RUNNING')
            ORDER BY j.createdAt DESC
            """)
    List<AnalysisJob> findActiveReanalyzeJobs(@Param("requirementId") String requirementId);

    /** requirement 기준 최근 REQUIREMENT_INSIGHT_REANALYZE Job 목록 (상태 무관, 최대 limit건) */
    @Query("""
            SELECT j FROM AnalysisJob j
            WHERE j.targetRequirementId = :requirementId
              AND j.jobType = 'REQUIREMENT_INSIGHT_REANALYZE'
            ORDER BY j.createdAt DESC
            """)
    List<AnalysisJob> findReanalyzeJobsByRequirementId(
            @Param("requirementId") String requirementId,
            org.springframework.data.domain.Pageable pageable);

    /** ID 목록으로 Job 일괄 조회 */
    List<AnalysisJob> findByIdIn(List<String> ids);

    /** 프로젝트 내 모든 REQUIREMENT_INSIGHT_REANALYZE Job (최신순) */
    @Query("""
            SELECT j FROM AnalysisJob j
            WHERE j.projectId = :projectId
              AND j.jobType = 'REQUIREMENT_INSIGHT_REANALYZE'
            ORDER BY j.createdAt DESC
            """)
    List<AnalysisJob> findReanalyzeJobsByProjectId(@Param("projectId") String projectId);

    /** 폴링 워커: PENDING 상태 Job 조회 */
    @Query("SELECT j FROM AnalysisJob j WHERE j.status = 'PENDING' ORDER BY j.createdAt ASC")
    List<AnalysisJob> findPendingJobs();

    /** 타임아웃 감지: RUNNING이 cutoff 이전에 시작된 Job */
    @Query("SELECT j FROM AnalysisJob j WHERE j.status = 'RUNNING' AND j.startedAt < :cutoff")
    List<AnalysisJob> findStuckRunningJobs(@Param("cutoff") LocalDateTime cutoff);

    /** 진행률 + 단계명 직접 업데이트 (self-invocation 트랜잭션 문제 회피) */
    @Modifying
    @Transactional
    @Query("UPDATE AnalysisJob j SET j.progress = :progress, j.progressStep = :step WHERE j.id = :jobId")
    void updateProgressById(@Param("jobId") String jobId,
                            @Param("progress") int progress,
                            @Param("step") String step);
}

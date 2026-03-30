package com.bidops.domain.requirement.repository;

import com.bidops.domain.requirement.entity.RequirementInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RequirementInsightRepository extends JpaRepository<RequirementInsight, String> {

    Optional<RequirementInsight> findByRequirementId(String requirementId);

    @Modifying @Transactional
    void deleteByRequirementId(String requirementId);

    /** 프로젝트 내 quality_issues가 있는 insight 조회 */
    @Query("""
            SELECT i FROM RequirementInsight i
            WHERE i.requirementId IN (
                SELECT r.id FROM Requirement r WHERE r.projectId = :projectId AND r.archived = false
            )
            AND i.qualityIssuesJson IS NOT NULL
            AND i.qualityIssuesJson <> ''
            AND i.qualityIssuesJson <> '[]'
            """)
    List<RequirementInsight> findInsightsWithIssuesByProjectId(@Param("projectId") String projectId);
}

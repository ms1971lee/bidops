package com.bidops.domain.requirement.repository;

import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.enums.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequirementRepository extends JpaRepository<Requirement, String> {

    /**
     * GET /projects/{projectId}/requirements
     * 모든 필터 optional - openapi.yaml 쿼리 파라미터 전체 반영
     */
    @Query("""
            SELECT r FROM Requirement r
            WHERE r.projectId = :projectId
              AND r.archived = false
              AND (:category        IS NULL OR r.category        = :category)
              AND (:mandatory       IS NULL OR r.mandatoryFlag   = :mandatory)
              AND (:evidenceReq     IS NULL OR r.evidenceRequiredFlag = :evidenceReq)
              AND (:analysisStatus  IS NULL OR r.analysisStatus  = :analysisStatus)
              AND (:reviewStatus    IS NULL OR r.reviewStatus    = :reviewStatus)
              AND (:factLevel       IS NULL OR r.factLevel       = :factLevel)
              AND (:queryNeeded     IS NULL OR r.queryNeeded     = :queryNeeded)
              AND (:keyword         IS NULL
                   OR LOWER(r.title)        LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.originalText) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.requirementCode) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Requirement> search(
            @Param("projectId")      String projectId,
            @Param("category")       RequirementCategory category,
            @Param("mandatory")      Boolean mandatory,
            @Param("evidenceReq")    Boolean evidenceReq,
            @Param("analysisStatus") RequirementAnalysisStatus analysisStatus,
            @Param("reviewStatus")   RequirementReviewStatus reviewStatus,
            @Param("factLevel")      FactLevel factLevel,
            @Param("queryNeeded")    Boolean queryNeeded,
            @Param("keyword")        String keyword,
            Pageable pageable);

    Optional<Requirement> findByIdAndProjectId(String id, String projectId);

    boolean existsByDocumentIdAndOriginalText(String documentId, String originalText);

    List<Requirement> findByProjectIdAndQueryNeededTrue(String projectId);

    List<Requirement> findByDocumentId(String documentId);
}

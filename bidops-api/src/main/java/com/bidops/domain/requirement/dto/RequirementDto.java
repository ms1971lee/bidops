package com.bidops.domain.requirement.dto;

import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.enums.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * openapi.yaml Requirement schema.
 *
 * page_refs, clause_refs 는 /sources 엔드포인트(RequirementSourcesDto)에서만 제공.
 * 목록/상세 응답에는 포함하지 않는다 (N+1 및 성능 이슈 방지).
 */
@Getter
@Builder
public class RequirementDto {

    private String id;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("requirement_code")
    private String requirementCode;

    private String title;

    @JsonProperty("original_text")
    private String originalText;

    private RequirementCategory category;

    @JsonProperty("mandatory_flag")
    private boolean mandatoryFlag;

    @JsonProperty("evidence_required_flag")
    private boolean evidenceRequiredFlag;

    @JsonProperty("confidence_score")
    private Float confidenceScore;

    @JsonProperty("analysis_status")
    private RequirementAnalysisStatus analysisStatus;

    @JsonProperty("review_status")
    private RequirementReviewStatus reviewStatus;

    @JsonProperty("fact_level")
    private FactLevel factLevel;

    @JsonProperty("query_needed")
    private boolean queryNeeded;

    @JsonProperty("original_req_nos")
    private String originalReqNos;

    @JsonProperty("extraction_status")
    private String extractionStatus;

    @JsonProperty("merge_reason")
    private String mergeReason;

    @JsonProperty("archived")
    private boolean archived;

    @JsonProperty("visible")
    private boolean visible;

    @JsonProperty("extraction_status_v2")
    private String extractionStatusV2;

    @JsonProperty("enrichment_status")
    private String enrichmentStatus;

    @JsonProperty("quality_gate_status")
    private String qualityGateStatus;

    @JsonProperty("atomic_flag")
    private boolean atomicFlag;

    @JsonProperty("source_clause_id")
    private String sourceClauseId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static RequirementDto from(Requirement r) {
        return RequirementDto.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .documentId(r.getDocumentId())
                .requirementCode(r.getRequirementCode())
                .title(r.getTitle())
                .originalText(r.getOriginalText())
                .category(r.getCategory())
                .mandatoryFlag(r.isMandatoryFlag())
                .evidenceRequiredFlag(r.isEvidenceRequiredFlag())
                .confidenceScore(r.getConfidenceScore())
                .analysisStatus(r.getAnalysisStatus())
                .reviewStatus(r.getReviewStatus())
                .factLevel(r.getFactLevel())
                .queryNeeded(r.isQueryNeeded())
                .originalReqNos(r.getOriginalReqNos())
                .extractionStatus(r.getExtractionStatus())
                .mergeReason(r.getMergeReason())
                .archived(r.isArchived())
                .visible(r.isVisible())
                .extractionStatusV2(r.getExtractionStatusV2())
                .enrichmentStatus(r.getEnrichmentStatus())
                .qualityGateStatus(r.getQualityGateStatus())
                .atomicFlag(r.isAtomicFlag())
                .sourceClauseId(r.getSourceClauseId())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}

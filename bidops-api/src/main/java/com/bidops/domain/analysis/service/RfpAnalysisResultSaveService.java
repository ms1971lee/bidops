package com.bidops.domain.analysis.service;

import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.analysis.dto.RfpAnalysisResultItem;
import com.bidops.domain.analysis.dto.RfpAnalysisResultRequest;
import com.bidops.domain.analysis.dto.RfpAnalysisResultSaveResponse;
import com.bidops.domain.analysis.dto.RfpAnalysisValidationResponse.ItemWarning;
import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisResultStatus;
import com.bidops.domain.analysis.repository.AnalysisJobRepository;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.entity.RequirementReview;
import com.bidops.domain.requirement.entity.RequirementSource;
import com.bidops.domain.requirement.enums.FactLevel;
import com.bidops.domain.requirement.enums.RequirementAnalysisStatus;
import com.bidops.domain.requirement.enums.RequirementCategory;
import com.bidops.domain.requirement.enums.RequirementReviewStatus;
import com.bidops.domain.requirement.repository.RequirementInsightRepository;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.bidops.domain.requirement.repository.RequirementReviewRepository;
import com.bidops.domain.requirement.repository.RequirementSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RFP 분석 결과를 Requirement + RequirementInsight + SourceExcerpt + RequirementSource로 변환·저장한다.
 * DB 저장만 담당하며, 검증은 RfpAnalysisValidationService에서 수행한다.
 *
 * page_no가 있으면 SourceExcerpt를 생성(또는 기존 매칭)하고 RequirementSource로 연결한다.
 * page_no가 없으면 위치 정보를 RequirementInsight.factSummary에 임시 보존한다.
 *
 * @see docs/RFP_ANALYSIS_SAVE_MAPPING.md
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfpAnalysisResultSaveService {

    private final AnalysisJobRepository analysisJobRepository;
    private final RequirementRepository requirementRepository;
    private final RequirementInsightRepository requirementInsightRepository;
    private final RequirementReviewRepository requirementReviewRepository;
    private final SourceExcerptRepository sourceExcerptRepository;
    private final RequirementSourceRepository requirementSourceRepository;

    @Transactional
    public RfpAnalysisResultSaveResponse save(RfpAnalysisResultRequest request) {

        // 1. AnalysisJob 존재 확인 및 projectId 조회
        AnalysisJob job = analysisJobRepository.findById(request.getAnalysisJobId())
                .orElseThrow(() -> new BidOpsException(
                        HttpStatus.NOT_FOUND, "ANALYSIS_JOB_NOT_FOUND",
                        "AnalysisJob을 찾을 수 없습니다: " + request.getAnalysisJobId()));

        String projectId = job.getProjectId();
        String documentId = request.getDocumentId();

        // 1-1. Job 상태를 RUNNING으로 전환
        job.start();

        // 2. 카테고리별 채번 카운터
        Map<RequirementCategory, AtomicInteger> codeCounters = new HashMap<>();

        // 3. 항목별 변환·저장
        List<String> savedIds = new ArrayList<>();
        List<ItemWarning> warnings = new ArrayList<>();
        int skippedCount = 0;

        // 요청 내 중복 감지용 (같은 배치 내 동일 텍스트 방지)
        Set<String> seenTexts = new HashSet<>();

        List<RfpAnalysisResultItem> items = request.getResults();
        for (int i = 0; i < items.size(); i++) {
            RfpAnalysisResultItem item = items.get(i);

            // 3-0. 중복 체크: DB 기존 데이터 + 같은 배치 내 중복
            String reqText = item.getRequirementText();
            if (!seenTexts.add(reqText)) {
                warnings.add(ItemWarning.builder()
                        .index(i).field("requirement_text")
                        .message("같은 요청 내 중복 항목입니다. 저장을 건너뜁니다")
                        .build());
                skippedCount++;
                continue;
            }
            if (requirementRepository.existsByDocumentIdAndOriginalText(documentId, reqText)) {
                warnings.add(ItemWarning.builder()
                        .index(i).field("requirement_text")
                        .message("이미 저장된 요구사항입니다 (document_id + requirement_text 중복). 저장을 건너뜁니다")
                        .build());
                skippedCount++;
                continue;
            }

            // 3-1. category 변환
            RequirementCategory category = mapCategory(item.getRequirementType());

            // 3-2. requirementCode 채번
            AtomicInteger counter = codeCounters.computeIfAbsent(
                    category, k -> new AtomicInteger(0));
            String code = generateCode(category, counter.incrementAndGet());

            // 3-3. status → factLevel, queryNeeded 변환
            FactLevel factLevel = mapFactLevel(item.getStatus());
            boolean queryNeeded = item.getStatus() == AnalysisResultStatus.질의필요;

            // 3-4. title 생성 (원문 앞 80자)
            String title = truncate(item.getRequirementText(), 80);

            // 3-5. Requirement 생성
            Requirement requirement = Requirement.builder()
                    .projectId(projectId)
                    .documentId(documentId)
                    .requirementCode(code)
                    .title(title)
                    .originalText(item.getRequirementText())
                    .category(category)
                    .analysisStatus(RequirementAnalysisStatus.EXTRACTED)
                    .reviewStatus(RequirementReviewStatus.NOT_REVIEWED)
                    .factLevel(factLevel)
                    .queryNeeded(queryNeeded)
                    .build();

            requirementRepository.save(requirement);

            // 3-6. RequirementInsight 생성
            String factSummary = buildFactSummary(item);
            String riskNoteJson = item.getReviewRequiredNote() != null
                    ? "[\"" + item.getReviewRequiredNote().replace("\"", "\\\"") + "\"]"
                    : null;

            RequirementInsight insight = RequirementInsight.builder()
                    .requirementId(requirement.getId())
                    .factSummary(factSummary)
                    .interpretationSummary(item.getInferenceNote())
                    .riskNoteJson(riskNoteJson)
                    .queryNeeded(queryNeeded)
                    .factLevel(factLevel)
                    .generatedByJobId(request.getAnalysisJobId())
                    .build();

            requirementInsightRepository.save(insight);

            // 3-6b. RequirementReview 초기 생성 (NOT_REVIEWED)
            RequirementReview review = RequirementReview.builder()
                    .requirementId(requirement.getId())
                    .build();
            requirementReviewRepository.save(review);

            // 3-7. SourceExcerpt 생성/매칭 + RequirementSource 연결
            linkSourceExcerpt(documentId, requirement.getId(), item);

            savedIds.add(requirement.getId());

            // 3-8. 저장 시 경고 수집
            collectWarnings(i, item, category, warnings);
        }

        // 4. AnalysisJob 상태 갱신
        job.complete(savedIds.size());

        log.info("RFP 분석 결과 저장 완료: jobId={}, documentId={}, savedCount={}, skippedCount={}",
                request.getAnalysisJobId(), documentId, savedIds.size(), skippedCount);

        return RfpAnalysisResultSaveResponse.builder()
                .savedCount(savedIds.size())
                .skippedCount(skippedCount)
                .requirementIds(savedIds)
                .warnings(warnings)
                .build();
    }

    // ── SourceExcerpt 생성/매칭 + RequirementSource 연결 ────────────────

    private void linkSourceExcerpt(String documentId, String requirementId, RfpAnalysisResultItem item) {
        if (item.getPageNo() == null) {
            // 위치 정보 없음 → factSummary에 임시 보존 (buildFactSummary에서 처리됨)
            return;
        }

        // anchorLabel = clause_id 또는 section_path (clause_id 우선)
        String anchorLabel = item.getClauseId() != null ? item.getClauseId() : item.getSectionPath();

        // 기존 SourceExcerpt 매칭 시도 (documentId + pageNo + anchorLabel)
        SourceExcerpt excerpt = sourceExcerptRepository
                .findByDocumentIdAndPageNoAndAnchorLabel(documentId, item.getPageNo(), anchorLabel)
                .orElseGet(() -> {
                    // rawText: original_evidence + fact_basis 조합
                    String rawText = item.getOriginalEvidence();
                    if (item.getFactBasis() != null) {
                        rawText += "\n" + item.getFactBasis();
                    }

                    SourceExcerpt newExcerpt = SourceExcerpt.builder()
                            .documentId(documentId)
                            .pageNo(item.getPageNo())
                            .excerptType(SourceExcerpt.ExcerptType.PARAGRAPH)
                            .anchorLabel(anchorLabel)
                            .rawText(rawText)
                            .normalizedText(rawText)
                            .build();

                    return sourceExcerptRepository.save(newExcerpt);
                });

        // RequirementSource 연결
        RequirementSource source = RequirementSource.builder()
                .requirementId(requirementId)
                .sourceExcerptId(excerpt.getId())
                .linkType(RequirementSource.LinkType.PRIMARY)
                .build();

        requirementSourceRepository.save(source);
    }

    // ── 저장 시 경고 수집 ──────────────────────────────────────────────

    private void collectWarnings(int index, RfpAnalysisResultItem item,
                                 RequirementCategory mappedCategory, List<ItemWarning> warnings) {
        // page_no/clause_id 없이 '확인완료' 상태
        if (item.getPageNo() == null && item.getStatus() == AnalysisResultStatus.확인완료) {
            warnings.add(ItemWarning.builder()
                    .index(index).field("status")
                    .message("page_no가 없으면 status '확인완료'의 근거가 불충분합니다")
                    .build());
        }

        // requirementType → category fallback 발생
        if (mappedCategory == RequirementCategory.ETC
                && !"ETC".equals(item.getRequirementType())) {
            warnings.add(ItemWarning.builder()
                    .index(index).field("requirement_type")
                    .message("'" + item.getRequirementType() + "'은 현재 ETC로 매핑됩니다. enum 확장 필요")
                    .build());
        }

        // 위치 정보가 하나도 없음 — 근거 추적 불가
        if (item.getPageNo() == null && item.getClauseId() == null && item.getSectionPath() == null) {
            warnings.add(ItemWarning.builder()
                    .index(index).field("page_no")
                    .message("위치 정보(page_no, clause_id, section_path)가 모두 없습니다. 근거 확정값 아님")
                    .build());
        }
    }

    // ── status → FactLevel 변환 ─────────────────────────────────────

    private FactLevel mapFactLevel(AnalysisResultStatus status) {
        return switch (status) {
            case 확인완료     -> FactLevel.FACT;
            case 추정         -> FactLevel.INFERENCE;
            case 원문확인필요, 질의필요, 파싱한계 -> FactLevel.REVIEW_NEEDED;
        };
    }

    // ── requirementType → RequirementCategory 변환 ──────────────────
    // TODO: RequirementCategory enum 확장 후 1:1 매핑으로 전환

    private RequirementCategory mapCategory(String requirementType) {
        return switch (requirementType) {
            case "FUNCTIONAL", "DATA_INTEGRATION", "UI_UX" -> RequirementCategory.FUNCTIONAL;
            case "NON_FUNCTIONAL", "PERFORMANCE", "QUALITY", "INFRASTRUCTURE"
                    -> RequirementCategory.NON_FUNCTIONAL;
            case "SCHEDULE"      -> RequirementCategory.SCHEDULE;
            case "PERSONNEL"     -> RequirementCategory.STAFFING;
            case "TRACK_RECORD"  -> RequirementCategory.EXPERIENCE;
            case "SECURITY"      -> RequirementCategory.SECURITY;
            case "EVALUATION", "PRESENTATION" -> RequirementCategory.EVALUATION;
            case "DELIVERABLE"   -> RequirementCategory.DELIVERABLE;
            default              -> RequirementCategory.ETC;
        };
    }

    // ── requirementCode 자동 채번 ───────────────────────────────────

    private String generateCode(RequirementCategory category, int seq) {
        String prefix = switch (category) {
            case FUNCTIONAL     -> "SFR";
            case NON_FUNCTIONAL -> "NFR";
            case SCHEDULE       -> "SCH";
            case STAFFING       -> "STF";
            case EXPERIENCE     -> "EXP";
            case SECURITY       -> "SEC";
            case EVALUATION     -> "EVL";
            case DELIVERABLE    -> "DLV";
            case ETC            -> "ETC";
        };
        return String.format("%s-%03d", prefix, seq);
    }

    // ── factSummary 조합 (위치 정보 임시 보존) ──────────────────────

    private String buildFactSummary(RfpAnalysisResultItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getOriginalEvidence());

        List<String> locationParts = new ArrayList<>();
        if (item.getPageNo() != null)    locationParts.add("p." + item.getPageNo());
        if (item.getClauseId() != null)  locationParts.add(item.getClauseId());
        if (item.getSectionPath() != null) locationParts.add(item.getSectionPath());

        if (!locationParts.isEmpty()) {
            sb.append("\n[위치: ").append(String.join(", ", locationParts)).append("]");
        }
        if (item.getFactBasis() != null) {
            sb.append("\n[근거: ").append(item.getFactBasis()).append("]");
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}

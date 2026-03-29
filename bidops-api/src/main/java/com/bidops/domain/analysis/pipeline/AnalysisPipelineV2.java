package com.bidops.domain.analysis.pipeline;

import com.bidops.domain.analysis.entity.AnalysisIssue;
import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.entity.CoverageAudit;
import com.bidops.domain.analysis.entity.QualityGateResult;
import com.bidops.domain.analysis.intermediate.IntermediateFormat;
import com.bidops.domain.analysis.intermediate.IntermediateFormatBuilder;
import com.bidops.domain.analysis.quality.QualityGateService;
import com.bidops.domain.analysis.repository.AnalysisIssueRepository;
import com.bidops.domain.analysis.repository.CoverageAuditRepository;
import com.bidops.domain.analysis.worker.AnalysisJobHandler;
import com.bidops.domain.analysis.worker.DocumentExtractionResult;
import com.bidops.domain.analysis.worker.DocumentTextExtractor;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.DocumentRepository;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.entity.RequirementReview;
import com.bidops.domain.requirement.entity.RequirementSource;
import com.bidops.domain.requirement.enums.*;
import com.bidops.domain.requirement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * AI 분석 파이프라인 v2.
 * 3단계 분리: AtomicSplit → Enrichment → QualityGate + Save.
 *
 * dev/prod 프로파일에서 AiAnalysisJobHandler 대신 사용.
 */
@Slf4j
@Component
@Profile("!local")
@Order(5) // AiAnalysisJobHandler(Order=10)보다 높은 우선순위
@RequiredArgsConstructor
public class AnalysisPipelineV2 implements AnalysisJobHandler {

    private final DocumentTextExtractor textExtractor;
    private final IntermediateFormatBuilder intermediateBuilder;
    private final AtomicSplitService atomicSplitService;
    private final RequirementEnrichmentService enrichmentService;
    private final QualityGateService qualityGateService;
    private final RequirementRepository requirementRepository;
    private final RequirementInsightRepository insightRepository;
    private final RequirementSourceRepository sourceRepository;
    private final RequirementReviewRepository reviewRepository;
    private final SourceExcerptRepository excerptRepository;
    private final CoverageAuditRepository coverageAuditRepository;
    private final AnalysisIssueRepository issueRepository;
    private final DocumentRepository documentRepository;

    @Override
    public boolean supports(AnalysisJob job) {
        return true;
    }

    @Override
    public int execute(AnalysisJob job) {
        return execute(job, p -> {});
    }

    @Override
    public int execute(AnalysisJob job, ProgressCallback callback) {
        log.info("[PipelineV2] 시작: jobId={} documentId={}", job.getId(), job.getDocumentId());

        // 0. 기존 데이터 archive (삭제하지 않음)
        archiveExistingData(job.getDocumentId());
        callback.report(5);

        // 1. 텍스트 추출 + 중간 포맷
        DocumentExtractionResult extraction = textExtractor.extractStructured(job.getDocumentId());
        IntermediateFormat intermediate = intermediateBuilder.build(
                extraction, job.getDocumentId(), job.getProjectId(), null, null);
        log.info("[PipelineV2] 중간 포맷: blocks={} catalog={} expected={}",
                intermediate.getBlocks().size(), intermediate.getCatalogItems().size(), intermediate.getExpectedTotal());
        callback.report(15);

        // ═══ Stage A: Atomic Split ═══
        AtomicSplitService.SplitResult splitResult = atomicSplitService.split(intermediate);
        List<AtomicSplitService.AtomicItem> atomicItems = splitResult.getItems();
        log.info("[PipelineV2] Stage A 완료: {}건 atomic (기대 {}건)", atomicItems.size(), intermediate.getExpectedTotal());
        callback.report(40);

        if (atomicItems.isEmpty()) {
            log.warn("[PipelineV2] atomic split 결과 0건 — 종료");
            saveCoverageAudit(job, intermediate, List.of(), List.of(), List.of());
            return 0;
        }

        // ═══ Stage B: Enrichment ═══
        List<RequirementEnrichmentService.EnrichmentResult> enrichments = enrichmentService.enrich(atomicItems);
        log.info("[PipelineV2] Stage B 완료: {}건 분석", enrichments.size());
        callback.report(70);

        // ═══ Stage C: Save + QualityGate ═══
        Map<RequirementCategory, AtomicInteger> codeCounters = new HashMap<>();
        List<String> savedOriginalNos = new ArrayList<>();
        List<String> qgFailedNos = new ArrayList<>();
        List<String> visibleOriginalNos = new ArrayList<>();
        int savedCount = 0;

        for (int i = 0; i < atomicItems.size(); i++) {
            AtomicSplitService.AtomicItem item = atomicItems.get(i);
            RequirementEnrichmentService.EnrichmentResult enrichment = i < enrichments.size() ? enrichments.get(i) : null;

            // 카테고리 매핑
            RequirementCategory category;
            try { category = RequirementCategory.valueOf(item.getRequirementType()); }
            catch (Exception e) { category = RequirementCategory.ETC; }

            // 코드 채번
            AtomicInteger counter = codeCounters.computeIfAbsent(category, k -> new AtomicInteger(0));
            String code = generateCode(category, counter.incrementAndGet());

            // FactLevel 결정
            FactLevel factLevel = FactLevel.REVIEW_NEEDED;
            if (enrichment != null && enrichment.getFactLevel() != null) {
                try { factLevel = FactLevel.valueOf(enrichment.getFactLevel()); }
                catch (Exception ignored) {}
            }

            // Requirement 저장
            Requirement req = Requirement.builder()
                    .projectId(job.getProjectId())
                    .documentId(job.getDocumentId())
                    .requirementCode(code)
                    .title(item.getRequirementText() != null && item.getRequirementText().length() > 80
                            ? item.getRequirementText().substring(0, 80) : item.getRequirementText())
                    .originalText(item.getRequirementText())
                    .category(category)
                    .mandatoryFlag(item.isMandatory())
                    .evidenceRequiredFlag(item.isMandatory())
                    .analysisStatus(RequirementAnalysisStatus.EXTRACTED)
                    .reviewStatus(RequirementReviewStatus.NOT_REVIEWED)
                    .factLevel(factLevel)
                    .queryNeeded(enrichment != null && enrichment.isQueryNeeded())
                    .originalReqNos(item.getOriginalRequirementNos())
                    .extractionStatus("SINGLE")
                    .sourceClauseId(item.getClauseId())
                    .atomicFlag(true)
                    .extractionStatusV2("SPLIT")
                    .enrichmentStatus(enrichment != null ? "ENRICHED" : "ENRICHMENT_FAILED")
                    .qualityGateStatus("PENDING")
                    .analysisJobVersion(getNextVersion(job.getDocumentId()))
                    .archived(false)
                    .visible(true) // QG 후 변경 가능
                    .build();
            requirementRepository.save(req);

            // RequirementInsight 저장
            // mission_to_solve → intentSummary에 우선 저장 (v2에서는 mission이 더 구체적)
            String intentValue = enrichment != null
                    ? (enrichment.getMissionToSolve() != null ? enrichment.getMissionToSolve() : enrichment.getIntentSummary())
                    : null;

            RequirementInsight insight = RequirementInsight.builder()
                    .requirementId(req.getId())
                    .factSummary(enrichment != null ? enrichment.getFactBasis() : null)
                    .interpretationSummary(enrichment != null ? enrichment.getInferenceNote() : null)
                    .intentSummary(intentValue)
                    .proposalPoint(enrichment != null ? enrichment.getProposalPoint() : null)
                    .implementationApproach(enrichment != null ? enrichment.getImplementationApproach() : null)
                    .expectedDeliverablesJson(enrichment != null && enrichment.getExpectedDeliverables() != null
                            ? toJsonArray(enrichment.getExpectedDeliverables()) : null)
                    .differentiationPoint(enrichment != null ? enrichment.getDifferentiationPoint() : null)
                    .riskNoteJson(enrichment != null && enrichment.getRiskNote() != null
                            ? "[\"" + enrichment.getRiskNote().replace("\"", "\\\"") + "\"]" : null)
                    .queryNeeded(enrichment != null && enrichment.isQueryNeeded())
                    .factLevel(factLevel)
                    .generatedByJobId(job.getId())
                    .evaluationFocus(enrichment != null ? enrichment.getEvaluationFocus() : null)
                    .requiredEvidence(enrichment != null ? enrichment.getRequiredEvidence() : null)
                    .draftProposalSnippet(enrichment != null ? enrichment.getDraftProposalSnippet() : null)
                    .clarificationQuestions(enrichment != null ? enrichment.getClarificationQuestions() : null)
                    .splitPromptVersion(splitResult.getPromptVersion())
                    .analysisPromptVersion(RequirementEnrichmentService.PROMPT_VERSION)
                    .build();
            insightRepository.save(insight);

            // RequirementReview 초기화
            reviewRepository.save(RequirementReview.builder().requirementId(req.getId()).build());

            // SourceExcerpt + RequirementSource 연결
            if (item.getPageNo() != null) {
                String anchorLabel = item.getClauseId() != null ? item.getClauseId() : item.getOriginalRequirementNos();
                SourceExcerpt excerpt = excerptRepository.save(SourceExcerpt.builder()
                        .documentId(job.getDocumentId())
                        .pageNo(item.getPageNo())
                        .excerptType(SourceExcerpt.ExcerptType.PARAGRAPH)
                        .anchorLabel(anchorLabel)
                        .rawText(item.getSourceExcerpt() != null ? item.getSourceExcerpt() : item.getRequirementText())
                        .normalizedText(item.getRequirementText())
                        .build());
                sourceRepository.save(RequirementSource.builder()
                        .requirementId(req.getId())
                        .sourceExcerptId(excerpt.getId())
                        .linkType(RequirementSource.LinkType.PRIMARY)
                        .build());
            }

            // QualityGate 검증
            QualityGateResult qgResult = qualityGateService.evaluate(insight, job.getId());
            req.setQualityGateStatus(qgResult.getGateStatus());
            req.setVisible(!"FAIL".equals(qgResult.getGateStatus()));
            requirementRepository.save(req);

            // 추적
            String origNo = item.getOriginalRequirementNos() != null ? item.getOriginalRequirementNos() : code;
            savedOriginalNos.add(origNo);
            if ("FAIL".equals(qgResult.getGateStatus())) {
                qgFailedNos.add(origNo);
                // AnalysisIssue 저장
                issueRepository.save(AnalysisIssue.builder()
                        .projectId(job.getProjectId())
                        .documentId(job.getDocumentId())
                        .analysisJobId(job.getId())
                        .requirementId(req.getId())
                        .originalReqNo(origNo)
                        .clauseId(item.getClauseId())
                        .pageNo(item.getPageNo())
                        .issueType("QG_FAIL")
                        .failureReason(qgResult.getFailureReasons())
                        .build());
            } else {
                visibleOriginalNos.add(origNo);
            }

            savedCount++;
            callback.report(70 + (int)(25.0 * (i + 1) / atomicItems.size()));
        }

        // CoverageAudit 저장
        saveCoverageAudit(job, intermediate, savedOriginalNos, qgFailedNos, visibleOriginalNos);

        log.info("[PipelineV2] ═══ 완료 ═══");
        log.info("[PipelineV2]   기대: {}", intermediate.getExpectedTotal());
        log.info("[PipelineV2]   atomic split: {}", atomicItems.size());
        log.info("[PipelineV2]   저장: {}", savedCount);
        log.info("[PipelineV2]   QG 실패: {}", qgFailedNos.size());
        log.info("[PipelineV2]   visible: {}", visibleOriginalNos.size());

        callback.report(95);
        return savedCount;
    }

    // ── archive (삭제하지 않고 archived=true) ────────────────────

    private void archiveExistingData(String documentId) {
        List<Requirement> existing = requirementRepository.findByDocumentId(documentId);
        if (existing.isEmpty()) return;

        existing.stream()
                .filter(r -> !r.isArchived())
                .forEach(r -> {
                    r.setArchived(true);
                    r.setVisible(false);
                });
        requirementRepository.saveAll(existing);
        log.info("[PipelineV2] 기존 {}건 archived", existing.size());
    }

    private int getNextVersion(String documentId) {
        return requirementRepository.findByDocumentId(documentId).stream()
                .mapToInt(Requirement::getAnalysisJobVersion)
                .max().orElse(0) + 1;
    }

    // ── coverage audit ──────────────────────────────────────────

    private void saveCoverageAudit(AnalysisJob job, IntermediateFormat intermediate,
                                    List<String> savedNos, List<String> qgFailedNos, List<String> visibleNos) {
        Set<String> expectedNos = intermediate.getCatalogItems().stream()
                .map(IntermediateFormat.CatalogItem::getOriginalRequirementNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> detectedNos = intermediate.getExpectedBreakdown().entrySet().stream()
                .flatMap(e -> intermediate.getCatalogItems().stream()
                        .filter(c -> c.getGroupCode().equals(e.getKey()))
                        .map(IntermediateFormat.CatalogItem::getOriginalRequirementNo))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> savedSet = new LinkedHashSet<>(savedNos);
        Set<String> visibleSet = new LinkedHashSet<>(visibleNos);

        List<String> finalMissing = expectedNos.stream()
                .filter(no -> !visibleSet.contains(no))
                .toList();

        float coverageRate = expectedNos.isEmpty() ? 0 :
                Math.round(visibleSet.size() * 1000.0f / expectedNos.size()) / 10.0f;

        try {
            coverageAuditRepository.save(CoverageAudit.builder()
                    .projectId(job.getProjectId())
                    .documentId(job.getDocumentId())
                    .analysisJobId(job.getId())
                    .expectedCount(expectedNos.size())
                    .extractedCount(savedNos.size())
                    .savedCount(savedNos.size())
                    .mergedCount(0)
                    .missingCount(finalMissing.size())
                    .coverageRate(coverageRate)
                    .expectedOriginalNos(toJson(expectedNos))
                    .detectedOriginalNos(toJson(detectedNos))
                    .aiExtractedOriginalNos(toJson(savedSet))
                    .savedOriginalNos(toJson(savedSet))
                    .qgFailedNos(toJson(qgFailedNos))
                    .visibleCount(visibleSet.size())
                    .visibleOriginalNos(toJson(visibleSet))
                    .finalMissingNos(toJson(finalMissing))
                    .build());
        } catch (Exception e) {
            log.warn("[PipelineV2] CoverageAudit 저장 실패: {}", e.getMessage());
        }
    }

    // ── 유틸 ────────────────────────────────────────────────────

    private String generateCode(RequirementCategory category, int seq) {
        String prefix = switch (category) {
            case BUSINESS_OVERVIEW -> "BOV"; case BACKGROUND -> "BKG"; case OBJECTIVE -> "OBJ";
            case SCOPE -> "SCP"; case FUNCTIONAL -> "SFR"; case NON_FUNCTIONAL -> "NFR";
            case PERFORMANCE -> "PFM"; case SECURITY -> "SEC"; case QUALITY -> "QUA";
            case TESTING -> "TST"; case DATA_INTEGRATION -> "DAT"; case UI_UX -> "UIX";
            case INFRASTRUCTURE -> "INF"; case PERSONNEL -> "PSN"; case TRACK_RECORD -> "TRK";
            case SCHEDULE -> "SCH"; case DELIVERABLE -> "DLV"; case SUBMISSION -> "SUB";
            case PROPOSAL_GUIDE -> "PPG"; case EVALUATION -> "EVL"; case PRESENTATION -> "PRS";
            case MAINTENANCE -> "MNT"; case TRAINING -> "TRN"; case LEGAL -> "LGL"; case ETC -> "ETC";
        };
        return String.format("%s-%03d", prefix, seq);
    }

    private String toJsonArray(String commaSeparated) {
        if (commaSeparated == null) return null;
        return "[" + Arrays.stream(commaSeparated.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    private String toJson(Collection<String> items) {
        if (items == null || items.isEmpty()) return null;
        return "[\"" + String.join("\",\"", items) + "\"]";
    }
}

package com.bidops.domain.requirement.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ListData;
import com.bidops.domain.analysis.dto.AnalysisJobDto;
import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.repository.AnalysisJobRepository;
import com.bidops.domain.analysis.worker.AnalysisJobDispatcher;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import com.bidops.domain.requirement.dto.*;
import com.bidops.domain.requirement.entity.*;
import com.bidops.domain.requirement.enums.*;
import com.bidops.domain.requirement.repository.*;
import com.bidops.domain.requirement.service.RequirementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequirementServiceImpl implements RequirementService {

    private final RequirementRepository        requirementRepository;
    private final RequirementInsightRepository insightRepository;
    private final RequirementReviewRepository  reviewRepository;
    private final RequirementSourceRepository  sourceRepository;
    private final SourceExcerptRepository      excerptRepository;
    private final AnalysisJobRepository        analysisJobRepository;
    private final AnalysisJobDispatcher        jobDispatcher;
    private final ProjectAuthorizationService   authorizationService;
    private final ProjectActivityService       activityService;
    private final ObjectMapper                 objectMapper;

    private static final String REANALYZE_PROMPT_VERSION = "requirement_reanalyze_v1";

    // ── 목록 ─────────────────────────────────────────────────────────────────
    @Override
    public ListData<RequirementDto> listRequirements(
            String projectId,
            RequirementCategory category, Boolean mandatory, Boolean evidenceRequired,
            RequirementAnalysisStatus analysisStatus, RequirementReviewStatus reviewStatus,
            FactLevel factLevel, Boolean queryNeeded, String keyword,
            String qualityIssueCode, String qualitySeverity,
            int page, int size) {

        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);

        // quality 필터가 있으면 insight에서 해당 requirementId 집합을 먼저 구한다
        final java.util.Set<String> qualityFilterIds;
        if (qualityIssueCode != null || qualitySeverity != null) {
            var insights = insightRepository.findInsightsWithIssuesByProjectId(projectId);
            var ids = new java.util.HashSet<String>();
            for (var insight : insights) {
                var issues = RequirementInsightDto.parseQualityIssuesStatic(insight.getQualityIssuesJson());
                boolean match = issues.stream().anyMatch(qi ->
                        (qualityIssueCode == null || qualityIssueCode.equals(qi.getCode()))
                                && (qualitySeverity == null || qualitySeverity.equals(qi.getSeverity())));
                if (match) ids.add(insight.getRequirementId());
            }
            qualityFilterIds = ids;
        } else {
            qualityFilterIds = null;
        }

        PageRequest pageable = PageRequest.of(
                page - 1, size, Sort.by(Sort.Direction.ASC, "requirementCode"));

        Page<RequirementDto> result = requirementRepository
                .search(projectId, category, mandatory, evidenceRequired,
                        analysisStatus, reviewStatus, factLevel, queryNeeded, keyword, pageable)
                .map(RequirementDto::from);

        if (qualityFilterIds != null) {
            // Java 레벨 교차 필터 (DB 페이징 후 필터이므로 totalCount 재계산)
            var filtered = result.getContent().stream()
                    .filter(r -> qualityFilterIds.contains(r.getId()))
                    .toList();
            return new ListData<>(filtered, filtered.size());
        }

        return new ListData<>(result.getContent(), result.getTotalElements());
    }

    // ── 상세 (requirement + insight + review 조합) ────────────────────────────
    @Override
    public RequirementDetailDto getRequirement(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        Requirement req = findOrThrow(projectId, requirementId);

        RequirementInsightDto insight = insightRepository.findByRequirementId(requirementId)
                .map(RequirementInsightDto::from)
                .orElse(RequirementInsightDto.empty(requirementId));

        RequirementReviewDto review = reviewRepository.findByRequirementId(requirementId)
                .map(RequirementReviewDto::from)
                .orElse(RequirementReviewDto.empty(requirementId));

        return RequirementDetailDto.of(RequirementDto.from(req), insight, review);
    }

    // ── 요구사항 기본 정보 수정 ────────────────────────────────────────────────
    @Override
    @Transactional
    public RequirementDetailDto updateRequirement(String projectId, String requirementId,
                                                  RequirementUpdateRequest request) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_EDIT);
        Requirement req = findOrThrow(projectId, requirementId);
        req.update(request.getTitle(), request.getCategory(),
                   request.getMandatoryFlag(), request.getEvidenceRequiredFlag(),
                   request.getAnalysisStatus());
        activityService.record(projectId, ActivityType.REQUIREMENT_UPDATED,
                "요구사항 수정: " + req.getRequirementCode(),
                currentUserId(), requirementId, "requirement", null);
        return getRequirement(projectId, requirementId);
    }

    // ── 원문 근거 (SourceExcerpt 기반 동적 조합) ──────────────────────────────
    @Override
    public RequirementSourcesDto getSources(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        findOrThrow(projectId, requirementId);

        // Step1: RequirementSource 조회 (linkType 포함)
        var sources = sourceRepository.findByRequirementIdOrderByLinkTypeAsc(requirementId);
        if (sources.isEmpty()) {
            return RequirementSourcesDto.from(java.util.Collections.emptyList());
        }

        // Step2: SourceExcerpt 일괄 조회
        List<String> excerptIds = sources.stream()
                .map(rs -> rs.getSourceExcerptId()).distinct().toList();
        var excerptMap = excerptRepository.findAllByIdInOrdered(excerptIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.bidops.domain.document.entity.SourceExcerpt::getId,
                        e -> e, (a, b) -> a));

        return RequirementSourcesDto.from(sources, excerptMap);
    }

    // ── AI 분석 조회 ─────────────────────────────────────────────────────────
    @Override
    public RequirementInsightDto getInsight(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        findOrThrow(projectId, requirementId);
        return insightRepository.findByRequirementId(requirementId)
                .map(RequirementInsightDto::from)
                .orElse(RequirementInsightDto.empty(requirementId));
    }

    // ── AI 분석 수정 ─────────────────────────────────────────────────────────
    @Override
    @Transactional
    public RequirementInsightDto updateInsight(String projectId, String requirementId,
                                               RequirementAnalysisUpdateRequest request) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_EDIT);
        findOrThrow(projectId, requirementId);

        RequirementInsight insight = insightRepository.findByRequirementId(requirementId)
                .orElseGet(() -> RequirementInsight.builder()
                        .requirementId(requirementId)
                        .build());

        insight.update(
                request.getFactSummary(),
                request.getInterpretationSummary(),
                request.getIntentSummary(),
                request.getProposalPoint(),
                request.getImplementationApproach(),
                toJson(request.getExpectedDeliverables()),
                request.getDifferentiationPoint(),
                toJson(request.getRiskNote()),
                request.getQueryNeeded(),
                request.getFactLevel()
        );

        RequirementInsight saved = insightRepository.save(insight);
        activityService.record(projectId, ActivityType.REQUIREMENT_INSIGHT_UPDATED,
                "AI 분석 수정: " + requirementId,
                currentUserId(), requirementId, "requirement", null);
        return RequirementInsightDto.from(saved);
    }

    // ── 사람 검토 조회 ───────────────────────────────────────────────────────
    @Override
    public RequirementReviewDto getReview(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        findOrThrow(projectId, requirementId);
        return reviewRepository.findByRequirementId(requirementId)
                .map(RequirementReviewDto::from)
                .orElse(RequirementReviewDto.empty(requirementId));
    }

    // ── 검토 상태 변경 (OWNER만 가능: 승인 행위) ──────────────────────────────
    @Override
    @Transactional
    public RequirementReviewDto changeReviewStatus(String projectId, String requirementId,
                                                   RequirementReviewStatusChangeRequest request) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_APPROVE);
        Requirement req = findOrThrow(projectId, requirementId);

        RequirementReview review = reviewRepository.findByRequirementId(requirementId)
                .orElseGet(() -> RequirementReview.builder()
                        .requirementId(requirementId)
                        .build());

        String beforeStatus = review.getReviewStatus().name();
        review.changeStatus(request.getReviewStatus(), request.getReviewComment(), currentUserId());
        RequirementReview saved = reviewRepository.save(review);

        // Requirement 테이블에 비정규화 동기화 (목록 필터 성능용)
        req.syncReviewStatus(request.getReviewStatus());

        // before/after + comment 기록
        String detail = beforeStatus + " → " + request.getReviewStatus().name();
        if (request.getReviewComment() != null && !request.getReviewComment().isBlank()) {
            detail += " | " + request.getReviewComment();
        }
        activityService.record(projectId, ActivityType.REQUIREMENT_REVIEW_CHANGED,
                "요구사항 검토: " + req.getRequirementCode() + " " + beforeStatus + " → " + request.getReviewStatus(),
                currentUserId(), requirementId, "requirement", detail);

        return RequirementReviewDto.from(saved);
    }

    // ── 단건 재분석 ─────────────────────────────────────────────────────────
    @Override
    @Transactional
    public RequirementReanalyzeResponseDto reanalyzeRequirementInsight(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.ANALYSIS_RUN);

        // 1) requirement 검증
        Requirement req = findOrThrow(projectId, requirementId);

        // 2) 중복 재분석 차단: PENDING/RUNNING job이 있으면 409
        List<AnalysisJob> activeJobs = analysisJobRepository.findActiveReanalyzeJobs(requirementId);
        if (!activeJobs.isEmpty()) {
            AnalysisJob existing = activeJobs.get(0);
            throw BidOpsException.conflict(
                    "이미 진행 중인 재분석 Job이 있습니다. jobId=" + existing.getId()
                            + ", status=" + existing.getStatus());
        }

        // 3) source 조회 — 없으면 재분석 불가
        List<RequirementSource> sources = sourceRepository.findByRequirementIdOrderByLinkTypeAsc(requirementId);
        if (sources.isEmpty()) {
            throw BidOpsException.badRequest("재분석할 원문 근거(SourceExcerpt)가 없습니다. requirementId=" + requirementId);
        }

        List<String> excerptIds = sources.stream()
                .map(RequirementSource::getSourceExcerptId).distinct().toList();
        Map<String, SourceExcerpt> excerptMap = excerptRepository.findAllByIdInOrdered(excerptIds).stream()
                .collect(Collectors.toMap(SourceExcerpt::getId, e -> e, (a, b) -> a));

        // 3) AnalysisJob 생성
        AnalysisJob job = AnalysisJob.builder()
                .projectId(projectId)
                .documentId(req.getDocumentId())
                .targetRequirementId(requirementId)
                .jobType(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE)
                .analysisPromptVersion(REANALYZE_PROMPT_VERSION)
                .build();
        analysisJobRepository.save(job);

        // 4) 워커에 이벤트 디스패치 → 비동기 처리 시작
        log.info("Requirement 단건 재분석 Job 생성: jobId={}, requirementId={}, sourceCount={}",
                job.getId(), requirementId, excerptMap.size());
        jobDispatcher.dispatch(job);

        // 5) 활동 기록
        activityService.record(projectId, ActivityType.REQUIREMENT_INSIGHT_REANALYZED,
                "요구사항 재분석 요청: " + req.getRequirementCode(),
                currentUserId(), requirementId, "requirement", "jobId=" + job.getId());

        // 6) 현재 insight / review 조회 (review는 변경 없이 그대로 반환)
        RequirementInsightDto insightDto = insightRepository.findByRequirementId(requirementId)
                .map(RequirementInsightDto::from)
                .orElse(RequirementInsightDto.empty(requirementId));

        RequirementReviewDto reviewDto = reviewRepository.findByRequirementId(requirementId)
                .map(RequirementReviewDto::from)
                .orElse(RequirementReviewDto.empty(requirementId));

        return RequirementReanalyzeResponseDto.builder()
                .requirementId(requirementId)
                .analysisJobId(job.getId())
                .analysisStatus(job.getStatus())
                .insight(insightDto)
                .review(reviewDto)
                .build();
    }

    // ── 일괄 재분석 ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public BatchReanalyzeResponseDto batchReanalyze(String projectId, List<String> requirementIds) {
        requirePermission(projectId, ProjectPermission.ANALYSIS_RUN);

        List<String> createdJobIds = new java.util.ArrayList<>();
        List<BatchReanalyzeResponseDto.SkipReason> skipped = new java.util.ArrayList<>();

        for (String reqId : requirementIds) {
            try {
                // requirement 검증
                var req = requirementRepository.findByIdAndProjectId(reqId, projectId).orElse(null);
                if (req == null) {
                    skipped.add(BatchReanalyzeResponseDto.SkipReason.builder()
                            .requirementId(reqId).reason("요구사항을 찾을 수 없습니다.").build());
                    continue;
                }

                // 활성 job 차단
                if (!analysisJobRepository.findActiveReanalyzeJobs(reqId).isEmpty()) {
                    skipped.add(BatchReanalyzeResponseDto.SkipReason.builder()
                            .requirementId(reqId).reason("이미 진행 중인 재분석 Job이 있습니다.").build());
                    continue;
                }

                // source 검증
                if (sourceRepository.findByRequirementIdOrderByLinkTypeAsc(reqId).isEmpty()) {
                    skipped.add(BatchReanalyzeResponseDto.SkipReason.builder()
                            .requirementId(reqId).reason("원문 근거가 없습니다.").build());
                    continue;
                }

                // job 생성 + 디스패치
                AnalysisJob job = AnalysisJob.builder()
                        .projectId(projectId)
                        .documentId(req.getDocumentId())
                        .targetRequirementId(reqId)
                        .jobType(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE)
                        .analysisPromptVersion(REANALYZE_PROMPT_VERSION)
                        .build();
                analysisJobRepository.save(job);
                jobDispatcher.dispatch(job);
                createdJobIds.add(job.getId());

            } catch (Exception e) {
                log.warn("[BatchReanalyze] requirementId={} 실패: {}", reqId, e.getMessage());
                skipped.add(BatchReanalyzeResponseDto.SkipReason.builder()
                        .requirementId(reqId).reason(e.getMessage()).build());
            }
        }

        if (!createdJobIds.isEmpty()) {
            activityService.record(projectId, ActivityType.REQUIREMENT_INSIGHT_REANALYZED,
                    "일괄 재분석 요청: " + createdJobIds.size() + "건",
                    currentUserId(), null, "requirement", "jobCount=" + createdJobIds.size());
        }

        return BatchReanalyzeResponseDto.builder()
                .requestedCount(requirementIds.size())
                .createdJobCount(createdJobIds.size())
                .skippedCount(skipped.size())
                .createdJobIds(createdJobIds)
                .skippedReasons(skipped)
                .build();
    }

    // ── 일괄 재분석 상태 조회 ─────────────────────────────────────────────
    @Override
    public BatchReanalyzeStatusDto getBatchReanalyzeStatus(String projectId, List<String> jobIds) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);

        if (jobIds == null || jobIds.isEmpty()) {
            return BatchReanalyzeStatusDto.builder()
                    .totalJobs(0).done(true)
                    .failedJobs(List.of()).build();
        }

        var jobs = analysisJobRepository.findByIdIn(jobIds);

        int pending = 0, running = 0, completed = 0, failed = 0, cacheHit = 0;
        List<BatchReanalyzeStatusDto.FailedJob> failedJobs = new java.util.ArrayList<>();

        for (var job : jobs) {
            switch (job.getStatus()) {
                case PENDING -> pending++;
                case RUNNING -> running++;
                case COMPLETED -> { completed++; if (job.isCacheHit()) cacheHit++; }
                case FAILED -> {
                    failed++;
                    failedJobs.add(BatchReanalyzeStatusDto.FailedJob.builder()
                            .jobId(job.getId())
                            .requirementId(job.getTargetRequirementId())
                            .errorCode(job.getErrorCode())
                            .errorMessage(job.getErrorMessage())
                            .build());
                }
            }
        }

        return BatchReanalyzeStatusDto.builder()
                .totalJobs(jobs.size())
                .pendingCount(pending)
                .runningCount(running)
                .completedCount(completed)
                .failedCount(failed)
                .cacheHitCount(cacheHit)
                .done(pending == 0 && running == 0)
                .failedJobs(failedJobs)
                .build();
    }

    // ── 재분석 이력 조회 ────────────────────────────────────────────────────
    @Override
    public List<AnalysisJobDto> getReanalyzeHistory(String projectId, String requirementId, int limit) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        findOrThrow(projectId, requirementId);
        return analysisJobRepository
                .findReanalyzeJobsByRequirementId(requirementId, PageRequest.of(0, limit))
                .stream()
                .map(AnalysisJobDto::from)
                .toList();
    }

    // ── 품질 이슈 통계 ────────────────────────────────────────────────────
    @Override
    public QualityStatsDto getQualityStats(String projectId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);

        // REVIEW_NEEDED 수 + 전체 수
        var allReqs = requirementRepository.search(projectId,
                null, null, null, null, null, null, null, null,
                PageRequest.of(0, 1)).getTotalElements();
        var reviewNeededReqs = requirementRepository.search(projectId,
                null, null, null, null, null, FactLevel.REVIEW_NEEDED, null, null,
                PageRequest.of(0, 1)).getTotalElements();

        // quality_issues JSON 파싱 + 집계
        var insights = insightRepository.findInsightsWithIssuesByProjectId(projectId);

        java.util.Map<String, Integer> bySeverity = new java.util.LinkedHashMap<>();
        bySeverity.put("CRITICAL", 0);
        bySeverity.put("MINOR", 0);

        // code → {severity, message, count} 집계
        java.util.Map<String, int[]> countMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, String[]> metaMap = new java.util.LinkedHashMap<>(); // code → [severity, message]

        for (var insight : insights) {
            var issues = RequirementInsightDto.parseQualityIssuesStatic(insight.getQualityIssuesJson());
            for (var issue : issues) {
                bySeverity.merge(issue.getSeverity(), 1, Integer::sum);
                countMap.computeIfAbsent(issue.getCode(), k -> new int[]{0})[0]++;
                metaMap.putIfAbsent(issue.getCode(), new String[]{issue.getSeverity(), issue.getMessage()});
            }
        }

        var byCode = countMap.entrySet().stream()
                .map(e -> {
                    var meta = metaMap.get(e.getKey());
                    return QualityStatsDto.CodeCount.builder()
                            .code(e.getKey())
                            .severity(meta[0])
                            .message(meta[1])
                            .count(e.getValue()[0])
                            .build();
                })
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                .toList();

        return QualityStatsDto.builder()
                .reviewNeededCount((int) reviewNeededReqs)
                .totalRequirementCount((int) allReqs)
                .bySeverity(bySeverity)
                .byCode(byCode)
                .build();
    }

    // ── 프로젝트 내 최근 재분석 상태 맵 ──────────────────────────────────
    @Override
    public ReanalyzeStatusMapDto getReanalyzeStatusMap(String projectId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);

        var jobs = analysisJobRepository.findReanalyzeJobsByProjectId(projectId);

        // requirementId별 최신 1건만 (이미 createdAt DESC 정렬)
        java.util.Map<String, ReanalyzeStatusMapDto.ReqReanalyzeStatus> map = new java.util.LinkedHashMap<>();
        for (var job : jobs) {
            String reqId = job.getTargetRequirementId();
            if (reqId == null || map.containsKey(reqId)) continue;
            map.put(reqId, ReanalyzeStatusMapDto.ReqReanalyzeStatus.builder()
                    .status(job.getStatus().name())
                    .cacheHit(job.isCacheHit())
                    .errorMessage(job.getErrorMessage())
                    .errorCode(job.getErrorCode())
                    .finishedAt(job.getFinishedAt())
                    .progress(job.getProgress())
                    .progressStep(job.getProgressStep())
                    .build());
        }

        return ReanalyzeStatusMapDto.builder().items(map).build();
    }

    // ── internal helpers ─────────────────────────────────────────────────────
    private Requirement findOrThrow(String projectId, String requirementId) {
        return requirementRepository.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("요구사항"));
    }

    private void requirePermission(String projectId, ProjectPermission permission) {
        authorizationService.requirePermission(projectId, com.bidops.auth.SecurityUtils.currentUserId(), permission);
    }

    private String currentUserId() {
        return com.bidops.auth.SecurityUtils.currentUserId();
    }

    private String toJson(java.util.List<String> list) {
        if (list == null) return null;
        try { return objectMapper.writeValueAsString(list); }
        catch (JsonProcessingException e) { return "[]"; }
    }
}

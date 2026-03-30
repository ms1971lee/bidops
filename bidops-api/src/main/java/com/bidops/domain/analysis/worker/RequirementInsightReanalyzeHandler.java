package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.pipeline.OpenAiClient;
import com.bidops.domain.analysis.pipeline.OpenAiErrorCode;
import com.bidops.domain.analysis.pipeline.OpenAiException;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.entity.RequirementSource;
import com.bidops.domain.requirement.enums.FactLevel;
import com.bidops.domain.requirement.enums.QualityIssueCode;
import com.bidops.domain.requirement.repository.RequirementInsightRepository;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.bidops.domain.requirement.repository.RequirementSourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REQUIREMENT_INSIGHT_REANALYZE 전용 핸들러.
 *
 * 단일 requirement의 원문(originalText)과 원문 근거(SourceExcerpt)를 기반으로
 * OpenAI를 호출하여 RequirementInsight를 덮어쓴다.
 *
 * RequirementReview, RequirementSource, SourceExcerpt는 절대 수정하지 않는다.
 */
@Slf4j
@Component
@Order(1) // AnalysisPipelineV2(Order=5)보다 높은 우선순위
@RequiredArgsConstructor
public class RequirementInsightReanalyzeHandler implements AnalysisJobHandler {

    private final RequirementRepository requirementRepository;
    private final RequirementInsightRepository insightRepository;
    private final RequirementSourceRepository sourceRepository;
    private final SourceExcerptRepository excerptRepository;
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_PATH = "prompts/prompt_requirement_reanalyze_v2.txt";
    static final String PROMPT_VERSION = "requirement_reanalyze_v2";

    /** 품질 검증에서 "충분한 내용"으로 간주하는 최소 글자 수 */
    private static final int MIN_FIELD_LENGTH = 15;

    private String promptTemplate;

    // ── supports: REQUIREMENT_INSIGHT_REANALYZE 전용 ────────────────────

    @Override
    public boolean supports(AnalysisJob job) {
        return job.getJobType() == AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE;
    }

    // ── execute ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public int execute(AnalysisJob job, ProgressCallback callback) {
        String requirementId = job.getTargetRequirementId();
        if (requirementId == null || requirementId.isBlank()) {
            throw new OpenAiException(OpenAiErrorCode.TARGET_REQUIREMENT_MISSING,
                    "jobId=" + job.getId());
        }
        log.info("[ReanalyzeHandler] 시작: jobId={} requirementId={}", job.getId(), requirementId);

        // Step 1: Requirement 로드 (10%)
        callback.reportStep(10, "LOADING_REQUIREMENT");
        Requirement req = requirementRepository.findById(requirementId)
                .orElseThrow(() -> new OpenAiException(OpenAiErrorCode.REQUIREMENT_NOT_FOUND,
                        "requirementId=" + requirementId));

        // Step 2: SourceExcerpt 로드 (20%)
        callback.reportStep(20, "LOADING_SOURCES");
        List<RequirementSource> sources = sourceRepository.findByRequirementIdOrderByLinkTypeAsc(requirementId);
        if (sources.isEmpty()) {
            throw new OpenAiException(OpenAiErrorCode.SOURCE_NOT_FOUND,
                    "requirementId=" + requirementId);
        }

        List<String> excerptIds = sources.stream()
                .map(RequirementSource::getSourceExcerptId).distinct().toList();
        Map<String, SourceExcerpt> excerptMap = excerptRepository.findAllByIdInOrdered(excerptIds).stream()
                .collect(Collectors.toMap(SourceExcerpt::getId, e -> e, (a, b) -> a));

        // Step 3: fingerprint 계산 + 캐시 비교 (30%)
        callback.reportStep(30, "CHECKING_CACHE");
        String fingerprint = computeFingerprint(req, sources, excerptMap);
        log.info("[ReanalyzeHandler] fingerprint={}", fingerprint);

        RequirementInsight existingInsight = insightRepository.findByRequirementId(requirementId)
                .orElse(null);

        if (existingInsight != null
                && fingerprint.equals(existingInsight.getInputFingerprint())
                && PROMPT_VERSION.equals(existingInsight.getAnalysisPromptVersion())) {
            job.markCacheHit();
            callback.reportStep(100, "CACHE_HIT");
            log.info("[ReanalyzeHandler] 캐시 히트 — OpenAI 미호출: jobId={} requirementId={}",
                    job.getId(), requirementId);
            return 0;
        }

        // Step 4: 프롬프트 구성 (40%)
        callback.reportStep(40, "BUILDING_PROMPT");
        String systemPrompt = loadPromptTemplate();
        String userPrompt = buildUserPrompt(req, sources, excerptMap);
        log.debug("[ReanalyzeHandler] userPrompt length={}", userPrompt.length());

        // Step 5: OpenAI 호출 — 가장 오래 걸리는 구간 (50%)
        callback.reportStep(50, "CALLING_AI");
        String responseJson = openAiClient.chat(systemPrompt, userPrompt);
        log.debug("[ReanalyzeHandler] GPT response (first 500): {}",
                responseJson.length() > 500 ? responseJson.substring(0, 500) : responseJson);

        // Step 6: 응답 파싱 + 품질 검증 (80%)
        callback.reportStep(80, "PARSING_RESPONSE");
        InsightFields fields = validateQuality(parseResponse(responseJson), req.getOriginalText());

        // Step 7: insight 저장 (90%)
        callback.reportStep(90, "SAVING_INSIGHT");
        RequirementInsight insight = existingInsight != null ? existingInsight
                : RequirementInsight.builder().requirementId(requirementId).build();

        // qualityIssues → 구조화 JSON [{severity, code, message}, ...]
        String qualityIssuesJson = null;
        if (fields.qualityIssues != null && !fields.qualityIssues.isEmpty()) {
            try {
                var dtos = fields.qualityIssues.stream()
                        .map(c -> java.util.Map.of(
                                "severity", c.getSeverity(),
                                "code", c.name(),
                                "message", c.getMessage()))
                        .toList();
                qualityIssuesJson = objectMapper.writeValueAsString(dtos);
            } catch (Exception e) { qualityIssuesJson = "[]"; }
        }

        insight.overwriteForReanalysis(
                fields.factSummary, fields.interpretationSummary,
                fields.intentSummary, fields.proposalPoint,
                fields.implementationApproach, fields.expectedDeliverablesJson,
                fields.differentiationPoint, fields.riskNoteJson,
                fields.queryNeeded, fields.factLevel,
                fields.evaluationFocus, fields.requiredEvidence,
                fields.draftProposalSnippet, fields.clarificationQuestions,
                job.getId(), PROMPT_VERSION, fingerprint, qualityIssuesJson
        );
        insightRepository.save(insight);

        callback.reportStep(95, "DONE");
        log.info("[ReanalyzeHandler] 완료: jobId={} requirementId={} factLevel={}",
                job.getId(), requirementId, fields.factLevel);

        return 1;
    }

    @Override
    public int execute(AnalysisJob job) {
        return execute(job, percent -> {}); // no-op callback
    }

    // ── fingerprint ────────────────────────────────────────────────────

    /**
     * 재분석 입력의 SHA-256 fingerprint를 계산한다.
     * 구성: promptVersion + originalText + 각 SourceExcerpt(pageNo + anchorLabel + text)
     * 정렬: excerptId 기준으로 정렬하여 순서 불변 보장.
     */
    String computeFingerprint(Requirement req, List<RequirementSource> sources,
                              Map<String, SourceExcerpt> excerptMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("v=").append(PROMPT_VERSION).append('\n');
        sb.append("t=").append(req.getOriginalText()).append('\n');

        // excerptId 기준 정렬 → 순서 불변
        sources.stream()
                .map(RequirementSource::getSourceExcerptId)
                .distinct()
                .sorted()
                .forEach(eid -> {
                    SourceExcerpt e = excerptMap.get(eid);
                    if (e == null) return;
                    sb.append("e=").append(e.getPageNo());
                    sb.append('|').append(e.getAnchorLabel() != null ? e.getAnchorLabel() : "");
                    sb.append('|').append(e.getNormalizedText() != null ? e.getNormalizedText() : e.getRawText());
                    sb.append('\n');
                });

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 사용 불가", e);
        }
    }

    // ── 프롬프트 구성 ──────────────────────────────────────────────────

    private String loadPromptTemplate() {
        if (promptTemplate == null) {
            try {
                var resource = new ClassPathResource(PROMPT_PATH);
                promptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new OpenAiException(OpenAiErrorCode.PROMPT_LOAD_ERROR,
                        PROMPT_PATH + ": " + e.getMessage(), e);
            }
        }
        return promptTemplate;
    }

    String buildUserPrompt(Requirement req, List<RequirementSource> sources,
                           Map<String, SourceExcerpt> excerptMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 재분석 대상 요구사항\n\n");
        sb.append("requirement_code: ").append(req.getRequirementCode()).append("\n");
        sb.append("category: ").append(req.getCategory()).append("\n");
        sb.append("original_text:\n").append(req.getOriginalText()).append("\n\n");

        sb.append("## 원문 근거 (SourceExcerpt)\n\n");
        for (RequirementSource rs : sources) {
            SourceExcerpt excerpt = excerptMap.get(rs.getSourceExcerptId());
            if (excerpt == null) continue;

            sb.append(String.format("[%s] page_no=%d | anchor_label=%s | link_type=%s\n",
                    excerpt.getId(),
                    excerpt.getPageNo(),
                    excerpt.getAnchorLabel() != null ? excerpt.getAnchorLabel() : "-",
                    rs.getLinkType().name()));

            String text = excerpt.getNormalizedText() != null ? excerpt.getNormalizedText() : excerpt.getRawText();
            if (text.length() > 1000) text = text.substring(0, 1000) + "...";
            sb.append("  text: ").append(text).append("\n\n");
        }

        sb.append("위 요구사항 1건에 대해 JSON 형식으로 심층 분석 결과를 반환하세요.\n");
        return sb.toString();
    }

    // ── 응답 파싱 ──────────────────────────────────────────────────────

    InsightFields parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // "analyses" 배열 내 첫 번째 항목 또는 루트 자체가 단건 결과일 수 있음
            JsonNode node = root;
            if (root.has("analyses") && root.get("analyses").isArray() && !root.get("analyses").isEmpty()) {
                node = root.get("analyses").get(0);
            }

            String factLevelStr = textOrDefault(node, "fact_level", "REVIEW_NEEDED");
            FactLevel factLevel;
            try {
                factLevel = FactLevel.valueOf(factLevelStr);
            } catch (IllegalArgumentException e) {
                log.warn("[ReanalyzeHandler] 알 수 없는 fact_level '{}', REVIEW_NEEDED로 fallback", factLevelStr);
                factLevel = FactLevel.REVIEW_NEEDED;
            }

            // expected_deliverables: 배열 → JSON string, 문자열 → ["item"] 래핑
            String deliverablesJson = serializeListField(node, "expected_deliverables");
            String riskNoteJson = serializeListField(node, "risk_note");

            return new InsightFields(
                    textOrNull(node, "fact_summary"),
                    textOrNull(node, "interpretation_summary"),
                    textOrNull(node, "intent_summary"),
                    textOrNull(node, "proposal_point"),
                    textOrNull(node, "implementation_approach"),
                    deliverablesJson,
                    textOrNull(node, "differentiation_point"),
                    riskNoteJson,
                    node.has("query_needed") && node.get("query_needed").asBoolean(),
                    factLevel,
                    textOrNull(node, "evaluation_focus"),
                    textOrNull(node, "required_evidence"),
                    textOrNull(node, "draft_proposal_snippet"),
                    textOrNull(node, "clarification_questions"),
                    List.<QualityIssueCode>of() // validateQuality에서 채움
            );
        } catch (OpenAiException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiException(OpenAiErrorCode.OPENAI_PARSE_ERROR,
                    "재분석 GPT 응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 파싱된 결과에 대해 품질 검증을 수행한다.
     *
     * 이슈를 치명(critical)과 일반(minor)으로 분리:
     * - 치명 이슈 1건 이상 → 즉시 REVIEW_NEEDED
     * - 일반 이슈 2건 이상 → REVIEW_NEEDED
     *
     * @param originalText requirement 원문 (원문 반복 탐지용)
     */
    InsightFields validateQuality(InsightFields fields, String originalText) {
        List<QualityIssueCode> issues = new java.util.ArrayList<>();

        // ── 치명 이슈 ────────────────────────────────────────────────
        if (fields.queryNeeded() && isWeak(fields.clarificationQuestions()))
            issues.add(QualityIssueCode.QUERY_CLARIFICATION_MISSING);
        if (isEmptyList(fields.expectedDeliverablesJson()))
            issues.add(QualityIssueCode.DELIVERABLES_EMPTY);
        if (isEmptyList(fields.riskNoteJson()))
            issues.add(QualityIssueCode.RISK_NOTE_EMPTY);
        if (!isWeak(fields.implementationApproach()) && originalText != null
                && resemblesOriginal(fields.implementationApproach(), originalText))
            issues.add(QualityIssueCode.IMPLEMENTATION_REPEATS_ORIGINAL);
        if (!isWeak(fields.proposalPoint()) && originalText != null
                && resemblesOriginal(fields.proposalPoint(), originalText))
            issues.add(QualityIssueCode.PROPOSAL_REPEATS_ORIGINAL);

        // ── 일반 이슈 ────────────────────────────────────────────────
        if (isWeak(fields.factSummary()))            issues.add(QualityIssueCode.FACT_SUMMARY_WEAK);
        if (isWeak(fields.interpretationSummary()))  issues.add(QualityIssueCode.INTERPRETATION_WEAK);
        if (isWeak(fields.intentSummary()))           issues.add(QualityIssueCode.INTENT_SUMMARY_WEAK);
        if (isWeak(fields.proposalPoint()))           issues.add(QualityIssueCode.PROPOSAL_POINT_WEAK);
        if (isWeak(fields.implementationApproach())) issues.add(QualityIssueCode.IMPLEMENTATION_WEAK);
        if (isWeak(fields.differentiationPoint()))   issues.add(QualityIssueCode.DIFFERENTIATION_WEAK);
        if (isWeak(fields.evaluationFocus()))         issues.add(QualityIssueCode.EVALUATION_FOCUS_WEAK);
        if (isWeak(fields.draftProposalSnippet()))   issues.add(QualityIssueCode.DRAFT_SNIPPET_WEAK);
        if (!isEmptyList(fields.expectedDeliverablesJson())
                && fields.expectedDeliverablesJson() != null
                && !fields.expectedDeliverablesJson().contains(","))
            issues.add(QualityIssueCode.DELIVERABLES_INSUFFICIENT);

        // 로그
        long criticalCount = issues.stream().filter(QualityIssueCode::isCritical).count();
        long minorCount = issues.size() - criticalCount;
        if (criticalCount > 0) log.warn("[ReanalyzeHandler] 치명 이슈 {}건", criticalCount);
        if (minorCount > 0) log.info("[ReanalyzeHandler] 일반 이슈 {}건", minorCount);

        // 강등 판정
        boolean downgrade = criticalCount > 0 || minorCount >= 2;
        FactLevel finalLevel = downgrade && fields.factLevel() != FactLevel.REVIEW_NEEDED
                ? FactLevel.REVIEW_NEEDED : fields.factLevel();

        if (downgrade && fields.factLevel() != FactLevel.REVIEW_NEEDED) {
            log.info("[ReanalyzeHandler] 품질 부족 → REVIEW_NEEDED 강등 (치명 {}건, 일반 {}건)",
                    criticalCount, minorCount);
        }

        return new InsightFields(
                fields.factSummary(), fields.interpretationSummary(),
                fields.intentSummary(), fields.proposalPoint(),
                fields.implementationApproach(), fields.expectedDeliverablesJson(),
                fields.differentiationPoint(), fields.riskNoteJson(),
                fields.queryNeeded(), finalLevel,
                fields.evaluationFocus(), fields.requiredEvidence(),
                fields.draftProposalSnippet(), fields.clarificationQuestions(),
                issues
        );
    }

    private boolean isWeak(String value) {
        return value == null || value.isBlank() || value.length() < MIN_FIELD_LENGTH;
    }

    private boolean isEmptyList(String json) {
        return json == null || json.equals("[]") || json.equals("null") || json.isBlank();
    }

    /**
     * AI 출력이 원문을 그대로 반복한 수준인지 판정.
     * 원문의 연속 단어 시퀀스가 출력에 60% 이상 포함되면 반복으로 판정.
     */
    static boolean resemblesOriginal(String output, String original) {
        if (original == null || original.length() < 20 || output == null) return false;

        // 공백 정규화 후 비교
        String normOriginal = original.replaceAll("\\s+", " ").trim();
        String normOutput = output.replaceAll("\\s+", " ").trim();

        if (normOriginal.isEmpty() || normOutput.isEmpty()) return false;

        // 원문을 10자 단위 청크로 나누어 출력에 포함 비율 계산
        int chunkSize = 10;
        int totalChunks = 0;
        int matchedChunks = 0;

        for (int i = 0; i <= normOriginal.length() - chunkSize; i += chunkSize) {
            String chunk = normOriginal.substring(i, i + chunkSize);
            totalChunks++;
            if (normOutput.contains(chunk)) {
                matchedChunks++;
            }
        }

        if (totalChunks == 0) return false;
        double ratio = (double) matchedChunks / totalChunks;
        return ratio >= 0.6;
    }

    private String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private String textOrDefault(JsonNode node, String field, String def) {
        String val = textOrNull(node, field);
        return val != null ? val : def;
    }

    private String serializeListField(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        JsonNode val = node.get(field);
        if (val.isArray()) {
            return val.toString(); // 이미 JSON array
        }
        // 문자열이면 쉼표 분리 후 배열로 변환
        String text = val.asText();
        if (text.isBlank()) return null;
        String[] items = text.split(",");
        try {
            return objectMapper.writeValueAsString(
                    java.util.Arrays.stream(items).map(String::trim).filter(s -> !s.isEmpty()).toList()
            );
        } catch (IOException e) {
            return "[\"" + text + "\"]";
        }
    }

    // ── 파싱 결과 내부 DTO ──────────────────────────────────────────────

    record InsightFields(
            String factSummary,
            String interpretationSummary,
            String intentSummary,
            String proposalPoint,
            String implementationApproach,
            String expectedDeliverablesJson,
            String differentiationPoint,
            String riskNoteJson,
            boolean queryNeeded,
            FactLevel factLevel,
            String evaluationFocus,
            String requiredEvidence,
            String draftProposalSnippet,
            String clarificationQuestions,
            List<QualityIssueCode> qualityIssues
    ) {}
}

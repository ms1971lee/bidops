package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.dto.RfpAnalysisResultItem;
import com.bidops.domain.analysis.dto.RfpAnalysisResultRequest;
import com.bidops.domain.analysis.dto.RfpAnalysisResultSaveResponse;
import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.enums.AnalysisResultStatus;
import com.bidops.domain.analysis.service.RfpAnalysisResultSaveService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.bidops.domain.analysis.entity.CoverageAudit;
import com.bidops.domain.analysis.intermediate.IntermediateFormat;
import com.bidops.domain.analysis.intermediate.IntermediateFormatBuilder;
import com.bidops.domain.analysis.repository.CoverageAuditRepository;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.repository.RequirementInsightRepository;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.bidops.domain.requirement.repository.RequirementSourceRepository;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.LinkedHashMap;

/**
 * 실제 AI 분석 핸들러 (dev/prod 프로파일).
 *
 * 흐름:
 * 1. StorageService에서 PDF 파일 로드
 * 2. PDF 텍스트 추출 (간이 처리 — Azure Document Intelligence 연동 시 교체)
 * 3. OpenAI GPT API 호출하여 RFP 분석
 * 4. 응답을 RfpAnalysisResultItem 목록으로 파싱
 * 5. RfpAnalysisResultSaveService로 일괄 저장
 *
 * DECISION NEEDED: Azure Document Intelligence OCR 연동 시 2단계 추출 로직 교체 필요
 */
@Slf4j
@Component
@Profile("legacy") // v2 파이프라인(AnalysisPipelineV2)으로 대체됨
@Order(10)
public class AiAnalysisJobHandler implements AnalysisJobHandler {

    private final RfpAnalysisResultSaveService saveService;
    private final DocumentTextExtractor textExtractor;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final RequirementRepository requirementRepository;
    private final RequirementInsightRepository insightRepository;
    private final RequirementSourceRepository sourceRepository;
    private final SourceExcerptRepository excerptRepository;
    private final CoverageAuditRepository coverageAuditRepository;
    private final IntermediateFormatBuilder intermediateFormatBuilder;

    @Value("${bidops.ai.openai-api-key:}")
    private String openaiApiKey;

    @Value("${bidops.ai.openai-model:gpt-4o}")
    private String openaiModel;

    @Value("${bidops.ai.openai-base-url:https://api.openai.com}")
    private String openaiBaseUrl;

    @Value("${bidops.ai.max-text-length:80000}")
    private int maxTextLength;

    @PostConstruct
    void logConfig() {
        log.info("[AiHandler] 활성화됨 — model={} baseUrl={} azureDI={}",
                openaiModel, openaiBaseUrl,
                "key설정" + (openaiApiKey.isBlank() ? "안됨" : "완료"));
    }

    private static final Set<AnalysisJobType> SUPPORTED_TYPES = Set.of(
            AnalysisJobType.RFP_PARSE,
            AnalysisJobType.REQUIREMENT_EXTRACTION,
            AnalysisJobType.PARSE
    );

    public AiAnalysisJobHandler(RfpAnalysisResultSaveService saveService,
                                 DocumentTextExtractor textExtractor,
                                 ObjectMapper objectMapper,
                                 RequirementRepository requirementRepository,
                                 RequirementInsightRepository insightRepository,
                                 RequirementSourceRepository sourceRepository,
                                 SourceExcerptRepository excerptRepository,
                                 CoverageAuditRepository coverageAuditRepository,
                                 IntermediateFormatBuilder intermediateFormatBuilder) {
        this.saveService = saveService;
        this.textExtractor = textExtractor;
        this.objectMapper = objectMapper;
        this.requirementRepository = requirementRepository;
        this.insightRepository = insightRepository;
        this.sourceRepository = sourceRepository;
        this.excerptRepository = excerptRepository;
        this.coverageAuditRepository = coverageAuditRepository;
        this.intermediateFormatBuilder = intermediateFormatBuilder;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public boolean supports(AnalysisJob job) {
        return SUPPORTED_TYPES.contains(job.getJobType());
    }

    @Override
    public int execute(AnalysisJob job) {
        return execute(job, (p) -> {});
    }

    @Override
    public int execute(AnalysisJob job, ProgressCallback callback) {
        log.info("[AiHandler] 분석 시작: jobId={} type={} documentId={}",
                job.getId(), job.getJobType(), job.getDocumentId());

        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            throw new RuntimeException("OpenAI API Key가 설정되지 않았습니다. bidops.ai.openai-api-key 설정을 확인하세요.");
        }

        callback.report(5);

        // 0. 재분석: 기존 데이터 정리
        cleanExistingData(job.getDocumentId());
        log.info("[AiHandler] 기존 데이터 정리 완료: documentId={}", job.getDocumentId());
        callback.report(10);

        // 1. PDF 텍스트 추출 (PDFBox — 페이지별 마커 포함)
        DocumentExtractionResult extraction = textExtractor.extractStructured(job.getDocumentId());
        log.info("[AiHandler] 문서 추출 완료: pages={} chars={} method={}",
                extraction.getTotalPages(), extraction.getFullText().length(), extraction.getExtractionMethod());

        callback.report(15);

        // 1.5. 중간 분석 포맷 생성
        // 문서 정보 조회
        var docOpt = requirementRepository.findByDocumentId(job.getDocumentId()); // just for title lookup
        String docTitle = extraction.getTotalPages() + "페이지 RFP";

        IntermediateFormat intermediate = intermediateFormatBuilder.build(
                extraction, job.getDocumentId(), job.getProjectId(), docTitle, null);

        Map<String, List<String>> detectedReqNos = new LinkedHashMap<>();
        intermediate.getExpectedBreakdown().forEach((k, v) -> {
            List<String> nos = intermediate.getCatalogItems().stream()
                    .filter(c -> c.getGroupCode().equals(k))
                    .map(IntermediateFormat.CatalogItem::getOriginalRequirementNo)
                    .toList();
            detectedReqNos.put(k, nos.isEmpty() ? List.of(k + "-???") : nos);
        });
        int expectedTotal = intermediate.getExpectedTotal();
        String expectedSummary = buildExpectedSummary(detectedReqNos);

        log.info("[AiHandler] 중간 포맷 생성: blocks={} catalog={} expected={}",
                intermediate.getBlocks().size(), intermediate.getCatalogItems().size(), expectedTotal);

        callback.report(30);

        // 2. OpenAI API 1차 호출 (normalized.md 기반)
        List<RfpAnalysisResultItem> items = new ArrayList<>(
                callOpenAiAnalysisWithExpected(intermediate.getNormalizedMarkdown(), expectedSummary, expectedTotal));
        log.info("[AiHandler] 1차 AI 분석: {}건 추출 (기대 {}건)", items.size(), expectedTotal);

        callback.report(50);

        // 2-1. 누락 보완 반복 호출 (기대 건수 미달 시 최대 3회)
        for (int pass = 2; pass <= 4; pass++) {
            if (expectedTotal > 0 && items.size() >= expectedTotal) break;
            if (expectedTotal == 0 && items.size() >= 20) break;
            List<RfpAnalysisResultItem> additional = callSupplementaryAnalysis(
                    intermediate.getNormalizedMarkdown(), items);
            if (additional.isEmpty()) {
                log.info("[AiHandler] {}차 보완: 추가 항목 없음 — 종료", pass);
                break;
            }
            log.info("[AiHandler] {}차 보완 분석: {}건 추가 (누적 {}건)", pass, additional.size(), items.size() + additional.size());
            items.addAll(additional);
            callback.report(50 + pass * 5);
        }
        log.info("[AiHandler] AI 분석 최종: {}건 (기대 {}건, 커버리지 {}%)",
                items.size(), expectedTotal,
                expectedTotal > 0 ? Math.round(items.size() * 100.0 / expectedTotal) : "N/A");

        callback.report(70);

        if (items.isEmpty()) {
            log.warn("[AiHandler] AI 분석 결과 0건: jobId={}", job.getId());
            callback.report(95);
            return 0;
        }

        // 3. 결과 저장 (기존 RfpAnalysisResultSaveService 활용)
        RfpAnalysisResultRequest request = RfpAnalysisResultRequest.builder()
                .analysisJobId(job.getId())
                .documentId(job.getDocumentId())
                .results(items)
                .build();

        callback.report(80);

        RfpAnalysisResultSaveResponse response = saveService.save(request);

        // 파이프라인 추출 통계 + CoverageAudit 저장
        long mergedCount = items.stream().filter(i -> "MERGED".equals(i.getExtractionStatus())).count();

        // 누락 번호 계산
        Set<String> extractedNos = new java.util.HashSet<>();
        items.forEach(i -> {
            if (i.getOriginalRequirementNos() != null) {
                for (String no : i.getOriginalRequirementNos().split(",")) {
                    extractedNos.add(no.trim().toUpperCase());
                }
            }
        });
        Set<String> allExpectedNos = new java.util.LinkedHashSet<>();
        detectedReqNos.values().forEach(allExpectedNos::addAll);
        List<String> missingNos = allExpectedNos.stream()
                .filter(no -> !extractedNos.contains(no.toUpperCase()))
                .toList();

        float coverageRate = expectedTotal > 0
                ? Math.round(response.getSavedCount() * 1000.0f / expectedTotal) / 10.0f : 0;

        // 카테고리별 요약
        StringBuilder catSummaryJson = new StringBuilder("{");
        detectedReqNos.forEach((prefix, nos) -> {
            long matched = nos.stream().filter(n -> extractedNos.contains(n.toUpperCase())).count();
            if (catSummaryJson.length() > 1) catSummaryJson.append(",");
            catSummaryJson.append("\"").append(prefix).append("\":{")
                    .append("\"expected\":").append(nos.size())
                    .append(",\"matched\":").append(matched)
                    .append(",\"missing\":").append(nos.size() - matched)
                    .append("}");
        });
        catSummaryJson.append("}");

        log.info("[AiHandler] ═══ 추출 커버리지 감사 ═══");
        log.info("[AiHandler]   기대 건수: {}", expectedTotal);
        log.info("[AiHandler]   AI 추출: {}", items.size());
        log.info("[AiHandler]   저장: {}", response.getSavedCount());
        log.info("[AiHandler]   스킵: {}", response.getSkippedCount());
        log.info("[AiHandler]   병합: {}", mergedCount);
        log.info("[AiHandler]   누락: {} ({})", missingNos.size(), String.join(", ", missingNos));
        log.info("[AiHandler]   커버리지: {}%", coverageRate);

        // CoverageAudit 저장
        try {
            coverageAuditRepository.save(CoverageAudit.builder()
                    .projectId(job.getProjectId())
                    .documentId(job.getDocumentId())
                    .analysisJobId(job.getId())
                    .expectedCount(expectedTotal)
                    .extractedCount(items.size())
                    .savedCount(response.getSavedCount())
                    .mergedCount((int) mergedCount)
                    .missingCount(missingNos.size())
                    .coverageRate(coverageRate)
                    .missingReqNos(missingNos.isEmpty() ? null : "[\"" + String.join("\",\"", missingNos) + "\"]")
                    .categorySummary(catSummaryJson.toString())
                    .build());
            log.info("[AiHandler] CoverageAudit 저장 완료");
        } catch (Exception e) {
            log.warn("[AiHandler] CoverageAudit 저장 실패: {}", e.getMessage());
        }

        if (response.getWarnings() != null && !response.getWarnings().isEmpty()) {
            response.getWarnings().forEach(w -> log.warn("[AiHandler]   경고: idx={} field={} msg={}", w.getIndex(), w.getField(), w.getMessage()));
        }

        callback.report(95);
        return response.getSavedCount();
    }

    // ── OpenAI API 호출 ─────────────────────────────────────────────────

    /**
     * 2차 보완 분석: 1차에서 추출된 항목 목록을 보여주고 누락된 것을 추가 추출.
     */
    private List<RfpAnalysisResultItem> callSupplementaryAnalysis(
            String documentText, List<RfpAnalysisResultItem> existingItems) {
        try {
            StringBuilder alreadyExtracted = new StringBuilder();
            for (int i = 0; i < existingItems.size(); i++) {
                var item = existingItems.get(i);
                alreadyExtracted.append(String.format("%d. [%s] %s\n",
                        i + 1, item.getRequirementType(),
                        item.getRequirementText().length() > 100
                                ? item.getRequirementText().substring(0, 100) + "..."
                                : item.getRequirementText()));
            }

            String truncated = documentText.length() > maxTextLength
                    ? documentText.substring(0, maxTextLength) : documentText;

            String supplementPrompt = """
                    이전 분석에서 아래 %d건의 요구사항이 추출되었습니다:

                    %s

                    그러나 원문 RFP 문서에는 위보다 더 많은 요구사항이 있습니다.
                    위에서 이미 추출된 항목은 제외하고, 아직 추출되지 않은 요구사항만 추가로 추출하세요.

                    ■ 반드시 확인할 누락 후보:
                    - 요구사항 목록표의 각 행 (MAR-001~006, DAR-001~004, MHR-001, SER-001~004, QUR-001~002, COR-001, PMR-001~004, PSR-001 등)
                    - 성능/품질/보안 요구사항 표의 세부 항목
                    - 투입 인력 자격/조건/경력 요구
                    - 제출 산출물 목록의 각 항목
                    - 평가 배점표의 각 평가 항목
                    - 유지보수/하자보수 기간/조건
                    - 개발 일정/납기/마일스톤 조건
                    - 계약/법적/보안 서약 조건
                    - 지적재산권/저작권 조건
                    - 하도급/외주 제한 조건

                    ■ 중요: 이미 추출된 %d건과 중복되는 항목은 절대 포함하지 마세요.
                    ■ 추가 항목이 없으면 빈 배열 {"requirements": []}을 반환하세요.

                    동일한 JSON 형식으로 응답하세요.

                    """.formatted(existingItems.size(), alreadyExtracted.toString(), existingItems.size()) + truncated;

            String systemPrompt = buildSystemPrompt();
            String responseJson = callChatCompletionApi(systemPrompt, supplementPrompt);
            return parseAnalysisResponse(responseJson);
        } catch (Exception e) {
            log.warn("[AiHandler] 보완 분석 실패 (무시): {}", e.getMessage());
            return List.of();
        }
    }

    private List<RfpAnalysisResultItem> callOpenAiAnalysis(String documentText) {
        String truncated = documentText.length() > maxTextLength
                ? documentText.substring(0, maxTextLength) : documentText;

        String systemPrompt = buildSystemPrompt();
        String userPrompt = """
                다음 RFP 문서를 분석하여 요구사항을 빠짐없이 전부 추출하세요.

                ■ 절대 규칙:
                - "대표 요구사항만 추출" 금지. 문서 전체 요구사항을 최소 단위로 분해하세요.
                - 표/목록/카테고리별 세부 요구사항을 모두 개별 항목으로 추출하세요.
                - 한 문장 내 복수 조건(A하고 B해야 한다)은 반드시 분리하세요.
                - 요구사항 표에 번호가 있으면(MAR-001 등) 그 번호를 original_requirement_nos에 기입하세요.
                - 기능, 성능, 보안뿐 아니라 일정, 인력, 실적, 제출물, 평가기준, 유지보수, 교육, 법률 조건도 전부 포함하세요.
                - 이 문서에 요구사항이 20건 이상 있을 가능성이 높습니다. 10건 미만이면 표/목록/별첨 누락입니다.

                ■ 특히 누락하기 쉬운 유형 (반드시 확인):
                - 요구사항 목록표의 각 행 (MAR-xxx, DAR-xxx, MHR-xxx, SER-xxx 등)
                - 성능/품질/보안 요구사항 표의 각 행
                - 제출 산출물 목록표의 각 항목
                - 평가 배점표의 각 평가 항목
                - 투입 인력 조건 (자격, 인원, 경력)
                - 유지보수/하자보수 조건
                - 일정/납기 조건
                - 법적 조건/계약 조건

                ■ 추출 완료 후 자체 검증:
                - 원문의 요구사항 표에 번호가 있으면 그 번호 수만큼 추출되었는지 확인하세요.
                - 누락이 있으면 추가하세요.
                - 추출 불가능한 항목은 status "파싱한계"로 포함하세요.

                """ + truncated;

        try {
            String responseJson = callChatCompletionApi(systemPrompt, userPrompt);
            return parseAnalysisResponse(responseJson);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI API 호출 실패: " + e.getMessage(), e);
        }
    }

    private String callChatCompletionApi(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", openaiModel,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.1,
                "max_tokens", 16000,
                "response_format", Map.of("type", "json_object")
        );

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new RuntimeException("요청 JSON 생성 실패", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create((openaiBaseUrl.endsWith("/v1") ? openaiBaseUrl : openaiBaseUrl + "/v1") + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofMinutes(5))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI API 통신 오류: " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI API 오류 (HTTP " + response.statusCode() + "): "
                    + truncateForLog(response.body(), 500));
        }

        // 응답에서 content 추출
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String finishReason = root.path("choices").path(0).path("finish_reason").asText();
            int totalTokens = root.path("usage").path("total_tokens").asInt();
            log.info("[AiHandler] GPT 응답: finish_reason={} total_tokens={}", finishReason, totalTokens);
            if ("length".equals(finishReason)) {
                log.warn("[AiHandler] ⚠ GPT 응답이 토큰 제한으로 잘렸습니다! max_tokens를 늘리세요.");
            }
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (IOException e) {
            throw new RuntimeException("OpenAI 응답 파싱 실패", e);
        }
    }

    private List<RfpAnalysisResultItem> parseAnalysisResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // AI 응답 형식: { "requirements": [ { ... }, ... ] }
            JsonNode requirementsNode = root.has("requirements") ? root.get("requirements") : root;
            if (requirementsNode.isArray()) {
                List<RfpAnalysisResultItem> items = new ArrayList<>();
                for (JsonNode node : requirementsNode) {
                    items.add(parseResultItem(node));
                }
                return items;
            }

            log.warn("[AiHandler] AI 응답에 requirements 배열이 없음: {}", truncateForLog(json, 200));
            return List.of();
        } catch (Exception e) {
            throw new RuntimeException("AI 분석 결과 파싱 실패: " + e.getMessage(), e);
        }
    }

    private RfpAnalysisResultItem parseResultItem(JsonNode node) {
        return RfpAnalysisResultItem.builder()
                .requirementText(getTextOrNull(node, "requirement_text"))
                .requirementType(getTextOrDefault(node, "requirement_type", "ETC"))
                .originalEvidence(getTextOrDefault(node, "original_evidence", ""))
                .status(parseStatus(getTextOrDefault(node, "status", "추정")))
                .pageNo(parsePageNo(node))
                .clauseId(getTextOrNull(node, "clause_id"))
                .sectionPath(getTextOrNull(node, "section_path"))
                .factBasis(getTextOrNull(node, "fact_basis"))
                .inferenceNote(getTextOrNull(node, "inference_note"))
                .reviewRequiredNote(getTextOrNull(node, "review_required_note"))
                .proposalPoint(getTextOrNull(node, "proposal_point"))
                .implementationDirection(getTextOrNull(node, "implementation_direction"))
                .deliverables(getTextOrNull(node, "deliverables"))
                .differentiation(getTextOrNull(node, "differentiation"))
                .risk(getTextOrNull(node, "risk"))
                .queryNeeded(node.has("query_needed") && !node.get("query_needed").isNull()
                        ? node.get("query_needed").asBoolean() : null)
                .mandatory(node.has("mandatory") && !node.get("mandatory").isNull()
                        ? node.get("mandatory").asBoolean() : null)
                .originalRequirementNos(getTextOrNull(node, "original_requirement_nos"))
                .extractionStatus(getTextOrDefault(node, "extraction_status", "SINGLE"))
                .mergeReason(getTextOrNull(node, "merge_reason"))
                .interpretation(getTextOrNull(node, "interpretation"))
                .evaluationFocus(getTextOrNull(node, "evaluation_focus"))
                .requiredEvidence(getTextOrNull(node, "required_evidence"))
                .draftProposalSnippet(getTextOrNull(node, "draft_proposal_snippet"))
                .clarificationQuestions(getTextOrNull(node, "clarification_questions"))
                .build();
    }

    private AnalysisResultStatus parseStatus(String value) {
        try {
            return AnalysisResultStatus.from(value);
        } catch (IllegalArgumentException e) {
            return AnalysisResultStatus.추정;
        }
    }

    // ── 시스템 프롬프트 ─────────────────────────────────────────────────

    private String buildSystemPrompt() {
        return """
                당신은 공공/민간 RFP(제안요청서) 분석 전문가입니다.
                10년 이상 SI/SM 프로젝트 수주 경험이 있으며, 제안서 평가위원 출신입니다.
                주어진 RFP 문서에서 요구사항을 구조적으로 추출하고, 각 요구사항에 대해 심층 분석을 수행합니다.

                ■ 핵심 원칙
                - 일반론/교과서적 서술 절대 금지. 이 RFP 문서의 구체적 맥락에 기반하여 분석하세요.
                - 원문 근거가 없는 내용은 반드시 status를 "추정" 또는 "질의필요"로 표시하세요.
                - 모든 분석은 "발주처가 왜 이것을 요구했는가"의 관점에서 서술하세요.
                - 제안서 작성자가 바로 활용할 수 있는 수준으로 구체적으로 작성하세요.

                ■ JSON 응답 형식 (반드시 준수):
                {
                  "requirements": [
                    {
                      "requirement_text": "요구사항 원문 (원문 그대로 발췌. 요약 금지)",
                      "requirement_type": "카테고리 (아래 목록에서 선택)",
                      "original_evidence": "원문 문장/문단을 그대로 발췌",
                      "status": "확인완료|원문확인필요|질의필요|추정|파싱한계",
                      "mandatory": true/false,
                      "page_no": 페이지번호(1부터 시작. 모르면 null),
                      "clause_id": "조항번호 (예: 3.2.1, 제4조. 없으면 null)",
                      "section_path": "섹션 경로 (예: 기능요구사항 > 보안. 없으면 null)",
                      "fact_basis": "원문에서 확인 가능한 구체적 근거. 어떤 표현이 이 요구사항을 뒷받침하는지 명시",
                      "inference_note": "추론이 포함된 경우: 어떤 문맥에서 추론했는지, 불확실한 부분은 무엇인지",
                      "review_required_note": "검토 필요 시: 무엇을 누구에게 확인해야 하는지 구체적으로",
                      "proposal_point": "제안서에서 강조할 포인트. 이 요구사항에서 평가위원이 기대하는 차별화된 대응 방안을 구체적으로 서술. 일반론 금지",
                      "implementation_direction": "구현 방향. 기술 스택, 아키텍처, 단계별 접근법 등을 이 요구사항의 맥락에 맞게 구체적으로 제시",
                      "deliverables": "이 요구사항과 관련하여 제출해야 할 산출물 목록 (쉼표 구분)",
                      "differentiation": "경쟁사 대비 차별화 포인트. 일반적 기술 나열이 아니라 이 RFP의 맥락에서 어떤 점이 차별화되는지",
                      "risk": "이 요구사항 이행 시 예상되는 리스크와 대응 방안. 구체적 시나리오 기반",
                      "interpretation": "발주처가 이 요구를 넣은 의도/배경 해석. 평가위원 관점에서 무엇을 보려는 건지 2문장 이상 분석. 빈값 금지",
                      "evaluation_focus": "평가위원이 이 항목에서 중점 확인할 사항. 예: 절차의 구체성, 산출물 양식 포함 여부, KPI 수치 명시 여부",
                      "required_evidence": "이 요구사항 충족을 증명하기 위한 증빙/근거 자료. 예: 유사 프로젝트 수행 실적, 인증서, 시스템 화면 캡처",
                      "draft_proposal_snippet": "제안서에 바로 넣을 수 있는 초안 문단 (200~400자). 표/목록/절차 형태 권장. 원문 복사가 아니라 제안서 문체로 작성",
                      "clarification_questions": "발주처에 질의해야 할 사항이 있으면 질의 문장으로 작성. 없으면 null",
                      "query_needed": true/false,
                      "original_requirement_nos": "원문에서 이 요구사항에 해당하는 항목 번호 (쉼표 구분). 예: 'MAR-001, MAR-002' 또는 '제3조 1항, 제3조 2항'. 원문에 번호가 없으면 페이지-순서 형태로 부여 (예: 'p5-1, p5-2')",
                      "extraction_status": "SINGLE(원문 1건=추출 1건) / MERGED(원문 여러 건을 하나로 병합한 경우)",
                      "merge_reason": "MERGED일 때만: 왜 병합했는지 사유"
                    }
                  ]
                }

                ■ 카테고리 (반드시 아래 중 하나):
                BUSINESS_OVERVIEW, BACKGROUND, OBJECTIVE, SCOPE,
                FUNCTIONAL, NON_FUNCTIONAL, PERFORMANCE, SECURITY,
                QUALITY, TESTING, DATA_INTEGRATION, UI_UX,
                INFRASTRUCTURE, PERSONNEL, TRACK_RECORD, SCHEDULE,
                DELIVERABLE, SUBMISSION, PROPOSAL_GUIDE, EVALUATION,
                PRESENTATION, MAINTENANCE, TRAINING, LEGAL, ETC

                ■ status 판별:
                - "확인완료": 원문에 명확히 기술 + page_no/clause_id 특정 가능
                - "원문확인필요": 원문 존재하나 해석 모호/불완전
                - "질의필요": 모호하여 발주처 질의서 필요 → query_needed=true
                - "추정": 명시 없으나 문맥상 추론 가능
                - "파싱한계": 표/이미지 등 텍스트 추출 불완전

                ■ mandatory 판별:
                - "~해야 한다", "~하여야 한다", "필수", "반드시", "의무" → true
                - "~할 수 있다", "권장", "바람직" → false
                - 평가 배점이 높거나 감점 기준이 있는 항목 → true

                ■ 규칙:
                1. 모든 요구사항을 빠짐없이 추출. "~해야 한다" 등 의무 표현 주의.
                2. original_evidence는 원문 그대로 발췌. 요약/재작성 금지.
                3. 중복 추출 금지. 유사 문장은 대표 1건만.
                4. "--- [페이지 N] ---" 마커 기준으로 page_no 정확히 기입.
                5. page_no는 1부터 시작. 0이나 음수 금지.
                6. clause_id는 원문 조항번호 그대로 기입 (예: "3.2.1", "제4조").
                7. 복합 문장은 개별 항목으로 분리.
                8. 표(테이블) 내 항목도 개별 추출.
                9. 산출물/제출서류/평가기준도 요구사항으로 추출.
                10. 권고 표현은 status "추정" + mandatory false.

                ■ 커버리지 규칙:
                - 원문의 모든 요구사항/조건/기준을 1건도 빠짐없이 추출하세요.
                - 원문에 번호가 있으면 (예: MAR-001, 제3조 등) original_requirement_nos에 반드시 기입하세요.
                - 원문에 번호가 없으면 "p{페이지}-{순서}" 형태로 부여하세요 (예: p5-1, p5-2).
                - 여러 원문 항목을 하나로 병합한 경우 extraction_status="MERGED", original_requirement_nos에 모든 원문 번호를 쉼표로 나열, merge_reason에 사유 기입.
                - 한 문장에 복수 조건이 있으면 반드시 개별 항목으로 분리 (SINGLE).
                - 추출 완료 후, 원문 전체 대비 빠진 항목이 없는지 자체 검증하세요.

                ■ 최우선 규칙: 건수 확보가 품질보다 우선.
                - 원문의 모든 요구사항을 빠짐없이 추출하는 것이 1순위.
                - 심화 분석 품질은 2순위. 건수가 기대치에 미달하면 안 됨.
                - 각 항목의 분석이 간결해도 되지만, 건수는 반드시 맞출 것.

                ■ 품질 기준 (간결하게):
                - interpretation: 빈값 금지. "왜 이것을 요구했는가" 1~2문장.
                - proposal_point: "무엇을 강조할지" + 구체적 방법. 추상 표현만 있으면 안 됨.
                - implementation_direction: 주체/절차/산출물 중 2개 이상 포함.
                - differentiation: 경쟁사 대비 구체적 차별 요소 1개 이상.
                - deliverables: 문서명 수준 나열.
                - risk: 시나리오 + 대응방안.

                ■ 절대 금지:
                - 원문 재진술만으로 분석 필드 채우기
                - interpretation 빈값
                """;
    }

    // ── 원문 요구사항 번호 패턴 감지 ───────────────────────────────────────

    private static final java.util.regex.Pattern REQ_NO_PATTERN = java.util.regex.Pattern.compile(
            "\\b(MAR|DAR|MHR|SER|QUR|COR|PMR|PSR|NFR|SFR|SEC|INF|PER|EVL|SCH|DEL|TRN|MNT|LGL)[-\\s]?(\\d{1,3})\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * PDF 텍스트에서 원문 요구사항 번호를 감지하여 카테고리별로 그룹핑.
     */
    private Map<String, List<String>> detectRequirementNumbers(String text) {
        Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        java.util.regex.Matcher matcher = REQ_NO_PATTERN.matcher(text);
        Set<String> seen = new java.util.LinkedHashSet<>();

        while (matcher.find()) {
            String prefix = matcher.group(1).toUpperCase();
            String no = matcher.group(2);
            String fullNo = prefix + "-" + String.format("%03d", Integer.parseInt(no));
            if (seen.add(fullNo)) {
                result.computeIfAbsent(prefix, k -> new ArrayList<>()).add(fullNo);
            }
        }
        return result;
    }

    private String buildExpectedSummary(Map<String, List<String>> detectedReqNos) {
        if (detectedReqNos.isEmpty()) return "패턴 감지 없음";
        StringBuilder sb = new StringBuilder();
        detectedReqNos.forEach((prefix, nos) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(prefix).append(" ").append(nos.size()).append("건");
            sb.append(" (").append(String.join(", ", nos)).append(")");
        });
        return sb.toString();
    }

    /**
     * 기대 건수를 포함한 GPT 호출.
     */
    private List<RfpAnalysisResultItem> callOpenAiAnalysisWithExpected(
            String documentText, String expectedSummary, int expectedTotal) {
        String truncated = documentText.length() > maxTextLength
                ? documentText.substring(0, maxTextLength) : documentText;

        String expectedClause = expectedTotal > 0
                ? """

                ■ 이 문서의 요구사항 기대 건수:
                %s
                총 %d건입니다.
                위 번호에 해당하는 요구사항을 1건도 빠짐없이 모두 추출하세요.
                각 추출 항목의 original_requirement_nos에 원문 번호(예: MAR-001)를 반드시 기입하세요.
                %d건보다 적게 추출하면 누락입니다. 불가능한 항목은 status "파싱한계"로 포함하세요.

                """.formatted(expectedSummary, expectedTotal, expectedTotal)
                : "";

        String userPrompt = """
                다음 RFP 문서를 분석하여 요구사항을 빠짐없이 전부 추출하세요.
                %s
                ■ 절대 규칙:
                - "대표 요구사항만 추출" 금지. 문서 전체 요구사항을 최소 단위로 분해하세요.
                - 표/목록/카테고리별 세부 요구사항을 모두 개별 항목으로 추출하세요.
                - 한 문장 내 복수 조건(A하고 B해야 한다)은 반드시 분리하세요.
                - 기능, 성능, 보안뿐 아니라 일정, 인력, 실적, 제출물, 평가기준, 유지보수, 교육, 법률 조건도 전부 포함하세요.

                """.formatted(expectedClause) + truncated;

        String systemPrompt = buildSystemPrompt();
        try {
            String responseJson = callChatCompletionApi(systemPrompt, userPrompt);
            return parseAnalysisResponse(responseJson);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI API 호출 실패: " + e.getMessage(), e);
        }
    }

    // ── 재분석 시 기존 데이터 정리 ────────────────────────────────────────

    private void cleanExistingData(String documentId) {
        // self-invocation @Transactional 문제 회피: 개별 repository 호출은 각각 auto-commit
        List<Requirement> existing = requirementRepository.findByDocumentId(documentId);
        if (existing.isEmpty()) return;

        for (Requirement req : existing) {
            try { sourceRepository.deleteByRequirementId(req.getId()); } catch (Exception e) { log.warn("source 삭제 실패: {}", e.getMessage()); }
            try { insightRepository.deleteByRequirementId(req.getId()); } catch (Exception e) { log.warn("insight 삭제 실패: {}", e.getMessage()); }
        }
        try { excerptRepository.deleteByDocumentId(documentId); } catch (Exception e) { log.warn("excerpt 삭제 실패: {}", e.getMessage()); }
        requirementRepository.deleteAllInBatch(existing);
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────

    /** page_no: null/0/음수 → null, 양수만 유효 */
    private Integer parsePageNo(JsonNode node) {
        if (!node.has("page_no") || node.get("page_no").isNull()) return null;
        int val = node.get("page_no").asInt();
        return val > 0 ? val : null;
    }

    private String getTextOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        String val = getTextOrNull(node, field);
        return val != null ? val : defaultValue;
    }

    private String truncateForLog(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}

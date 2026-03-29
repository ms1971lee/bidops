package com.bidops.domain.analysis.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage B: Requirement Enrichment.
 * 이미 분해된 atomic requirement 목록을 받아 심화 분석.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementEnrichmentService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public static final String PROMPT_VERSION = "analysis_v2";

    private String promptTemplate;

    private String getPromptTemplate() {
        if (promptTemplate == null) {
            try {
                var resource = new ClassPathResource("prompts/prompt_requirement_analysis_v2.txt");
                promptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("프롬프트 파일 로드 실패", e);
            }
        }
        return promptTemplate;
    }

    public String getPromptHash() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(getPromptTemplate().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) { return "unknown"; }
    }

    /**
     * atomic requirement 목록에 대한 심화 분석.
     * 입력: 분해된 requirement text 목록
     * 출력: 각 항목별 분석 결과
     */
    private static final int BATCH_SIZE = 10;

    public List<EnrichmentResult> enrich(List<AtomicSplitService.AtomicItem> items) {
        if (items.isEmpty()) return List.of();

        String systemPrompt = getPromptTemplate();
        List<EnrichmentResult> allResults = new ArrayList<>();

        // 배치 분할: BATCH_SIZE건씩 GPT 호출
        for (int batchStart = 0; batchStart < items.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, items.size());
            List<AtomicSplitService.AtomicItem> batch = items.subList(batchStart, batchEnd);

            log.info("[Enrichment] 배치 {}-{}/{} 분석 중...", batchStart + 1, batchEnd, items.size());

            try {
                StringBuilder userPrompt = new StringBuilder();
                userPrompt.append("아래 ").append(batch.size()).append("건의 atomic requirement에 대해 심화 분석하세요.\n\n");
                for (int i = 0; i < batch.size(); i++) {
                    var item = batch.get(i);
                    userPrompt.append(String.format("[%d] requirement_code=%s | clause_id=%s | page_no=%s\n",
                            i,
                            item.getOriginalRequirementNos() != null ? item.getOriginalRequirementNos() : "?",
                            item.getClauseId() != null ? item.getClauseId() : "?",
                            item.getPageNo() != null ? item.getPageNo() : "?"));
                    userPrompt.append("  requirement_text: ").append(item.getRequirementText()).append("\n");
                    if (item.getSourceExcerpt() != null) {
                        userPrompt.append("  source_excerpt: ").append(item.getSourceExcerpt().length() > 200
                                ? item.getSourceExcerpt().substring(0, 200) + "..." : item.getSourceExcerpt()).append("\n");
                    }
                    userPrompt.append("\n");
                }

                String responseJson = openAiClient.chat(systemPrompt, userPrompt.toString());
                log.debug("[Enrichment] GPT raw (first 500): {}", responseJson.length() > 500 ? responseJson.substring(0, 500) : responseJson);
            List<EnrichmentResult> batchResults = parseResponse(responseJson, batch.size());

                // index 보정 (배치 내 0-based → 전체 기준)
                for (int i = 0; i < batchResults.size(); i++) {
                    allResults.add(batchResults.get(i));
                }

                // 배치에서 빠진 항목은 빈 결과로 채우기
                for (int i = batchResults.size(); i < batch.size(); i++) {
                    allResults.add(EnrichmentResult.builder()
                            .requirementIndex(batchStart + i)
                            .factLevel("REVIEW_NEEDED")
                            .build());
                }
            } catch (Exception e) {
                log.warn("[Enrichment] 배치 {}-{} 실패: {}", batchStart + 1, batchEnd, e.getMessage());
                // 실패 배치는 빈 결과로 채우기
                for (int i = 0; i < batch.size(); i++) {
                    allResults.add(EnrichmentResult.builder()
                            .requirementIndex(batchStart + i)
                            .factLevel("REVIEW_NEEDED")
                            .build());
                }
            }
        }

        log.info("[Enrichment] 전체 완료: {}건 분석 (입력 {}건)", allResults.size(), items.size());
        return allResults;
    }

    private List<EnrichmentResult> parseResponse(String json, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.has("analyses") ? root.get("analyses") : root;

            List<EnrichmentResult> results = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    results.add(EnrichmentResult.builder()
                            .requirementIndex(node.has("requirement_index") ? node.get("requirement_index").asInt() : results.size())
                            .factBasis(textOrNull(node, "fact_basis"))
                            .inferenceNote(textOrNull(node, "inference_note"))
                            .intentSummary(textOrNull(node, "intent_summary"))
                            .proposalPoint(textOrNull(node, "proposal_point"))
                            .implementationApproach(textOrNull(node, "implementation_approach"))
                            .expectedDeliverables(textOrNull(node, "expected_deliverables"))
                            .differentiationPoint(textOrNull(node, "differentiation_point"))
                            .riskNote(textOrNull(node, "risk_note"))
                            .evaluationFocus(textOrNull(node, "evaluation_focus"))
                            .requiredEvidence(textOrNull(node, "required_evidence"))
                            .draftProposalSnippet(textOrNull(node, "draft_proposal_snippet"))
                            .clarificationQuestions(textOrNull(node, "clarification_questions"))
                            .queryNeeded(node.has("query_needed") && node.get("query_needed").asBoolean())
                            .factLevel(textOrDefault(node, "fact_level", "REVIEW_NEEDED"))
                            .missionToSolve(textOrNull(node, "mission_to_solve"))
                            .practicalExample(textOrNull(node, "practical_example"))
                            .proposalSectionMapping(textOrNull(node, "proposal_section_mapping"))
                            .referenceMaterials(node.has("reference_materials") && !node.get("reference_materials").isNull()
                                    ? node.get("reference_materials").toString() : null)
                            .build());
                }
            }
            log.info("[Enrichment] 분석 결과: {}건 (기대 {}건)", results.size(), expectedCount);
            return results;
        } catch (Exception e) {
            log.error("[Enrichment] 응답 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private String textOrNull(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    private String textOrDefault(JsonNode node, String field, String def) {
        String val = textOrNull(node, field);
        return val != null ? val : def;
    }

    @Getter @Builder
    public static class EnrichmentResult {
        private int requirementIndex;
        private String factBasis;
        private String inferenceNote;
        private String intentSummary;
        private String proposalPoint;
        private String implementationApproach;
        private String expectedDeliverables;
        private String differentiationPoint;
        private String riskNote;
        private String evaluationFocus;
        private String requiredEvidence;
        private String draftProposalSnippet;
        private String clarificationQuestions;
        private boolean queryNeeded;
        private String factLevel;
        // v2 추가 필드
        private String missionToSolve;
        private String practicalExample;
        private String proposalSectionMapping;
        private String referenceMaterials;
    }
}

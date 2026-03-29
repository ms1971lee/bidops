package com.bidops.domain.analysis.pipeline;

import com.bidops.domain.analysis.intermediate.IntermediateFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stage A: Atomic Requirement Split.
 * normalized.md + 기대 건수를 GPT에 투입하여 atomic requirement로 분해.
 * 분석하지 않음 — 분해만 수행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtomicSplitService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public static final String PROMPT_VERSION = "atomic_split_v1";

    @Value("${bidops.ai.max-text-length:80000}")
    private int maxTextLength;

    private String promptTemplate;

    private String getPromptTemplate() {
        if (promptTemplate == null) {
            try {
                var resource = new ClassPathResource("prompts/prompt_atomic_split_v1.txt");
                promptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("프롬프트 파일 로드 실패: prompt_atomic_split_v1.txt", e);
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
     * normalized.md 기반으로 atomic requirement 분해.
     */
    public SplitResult split(IntermediateFormat intermediate) {
        String systemPrompt = getPromptTemplate();

        String truncated = intermediate.getNormalizedMarkdown();
        if (truncated.length() > maxTextLength) {
            truncated = truncated.substring(0, maxTextLength);
        }

        // 기대 번호 목록을 상세하게 전달
        StringBuilder expectedInfo = new StringBuilder();
        if (intermediate.getExpectedTotal() > 0) {
            expectedInfo.append("\n\n■ 이 문서의 요구사항 기대 건수: ").append(intermediate.getExpectedTotal()).append("건\n");
            expectedInfo.append("■ 원문 요구사항 번호 목록 (반드시 모두 포함하세요):\n");
            intermediate.getCatalogItems().forEach(item ->
                    expectedInfo.append("  - ").append(item.getOriginalRequirementNo())
                            .append(" (p.").append(item.getPageNo()).append(")")
                            .append(": ").append(item.getOriginalTitle() != null ? item.getOriginalTitle().substring(0, Math.min(80, item.getOriginalTitle().length())) : "")
                            .append("\n"));
            expectedInfo.append("\n위 번호를 각각 original_requirement_nos에 기입하세요.\n");
            expectedInfo.append("MAR뿐 아니라 DAR, MHR, SER, QUR, COR, PMR, PSR도 반드시 포함하세요.\n\n");
        }

        String userPrompt = "다음 RFP 문서를 atomic requirement 단위로 분해하세요.\n"
                + expectedInfo + truncated;

        String responseJson = openAiClient.chat(systemPrompt, userPrompt);
        List<AtomicItem> items = parseResponse(responseJson);

        // 보완 호출 (건수 미달 또는 번호 누락 시)
        Set<String> extractedNos = items.stream()
                .map(AtomicItem::getOriginalRequirementNos)
                .filter(n -> n != null)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> expectedNos = intermediate.getCatalogItems().stream()
                .map(IntermediateFormat.CatalogItem::getOriginalRequirementNo)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> missingNos = expectedNos.stream()
                .filter(no -> !extractedNos.contains(no))
                .toList();

        if (!missingNos.isEmpty()) {
            log.info("[AtomicSplit] 1차: {}건 추출, 누락 번호 {}건: {}", items.size(), missingNos.size(), missingNos);
            List<AtomicItem> supplementary = callMissingSplit(systemPrompt, truncated, missingNos);
            items.addAll(supplementary);
            log.info("[AtomicSplit] 누락 보완 후: {}건", items.size());
        } else if (intermediate.getExpectedTotal() > 0 && items.size() < intermediate.getExpectedTotal()) {
            log.info("[AtomicSplit] 1차: {}건 (기대 {}건) — 보완 호출", items.size(), intermediate.getExpectedTotal());
            List<AtomicItem> supplementary = callSupplementary(systemPrompt, truncated, items);
            items.addAll(supplementary);
            log.info("[AtomicSplit] 보완 후: {}건", items.size());
        }

        log.info("[AtomicSplit] 최종: {}건 (기대 {}건)", items.size(), intermediate.getExpectedTotal());
        return SplitResult.builder()
                .items(items)
                .promptVersion(PROMPT_VERSION)
                .promptHash(getPromptHash())
                .build();
    }

    /**
     * 누락 번호 전용 보완 호출.
     * 특정 원문 번호가 추출되지 않았을 때 해당 번호만 집중 추출.
     */
    private List<AtomicItem> callMissingSplit(String systemPrompt, String documentText, List<String> missingNos) {
        String missingList = String.join(", ", missingNos);
        String prompt = "아래 원문 번호에 해당하는 요구사항이 아직 추출되지 않았습니다.\n"
                + "누락된 번호: " + missingList + "\n\n"
                + "이 번호들에 해당하는 요구사항을 문서에서 찾아 atomic requirement로 분해하세요.\n"
                + "각 항목의 original_requirement_nos에 해당 원문 번호를 반드시 기입하세요.\n"
                + "문서에서 해당 번호가 표의 행이나 조항으로 존재할 수 있습니다.\n\n"
                + documentText;

        try {
            String responseJson = openAiClient.chat(systemPrompt, prompt);
            return parseResponse(responseJson);
        } catch (Exception e) {
            log.warn("[AtomicSplit] 누락 보완 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<AtomicItem> callSupplementary(String systemPrompt, String documentText, List<AtomicItem> existing) {
        StringBuilder alreadyExtracted = new StringBuilder();
        for (int i = 0; i < existing.size(); i++) {
            alreadyExtracted.append(String.format("%d. [%s] %s\n",
                    i + 1, existing.get(i).getOriginalRequirementNos(),
                    existing.get(i).getRequirementText().length() > 80
                            ? existing.get(i).getRequirementText().substring(0, 80) + "..."
                            : existing.get(i).getRequirementText()));
        }

        String supplementPrompt = "이전에 " + existing.size() + "건이 분해되었습니다:\n\n"
                + alreadyExtracted
                + "\n위에서 빠진 요구사항을 추가로 분해하세요. 이미 있는 항목은 중복하지 마세요.\n\n"
                + documentText;

        try {
            String responseJson = openAiClient.chat(systemPrompt, supplementPrompt);
            return parseResponse(responseJson);
        } catch (Exception e) {
            log.warn("[AtomicSplit] 보완 호출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<AtomicItem> parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.has("atomic_requirements") ? root.get("atomic_requirements")
                    : root.has("requirements") ? root.get("requirements") : root;

            List<AtomicItem> items = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    items.add(AtomicItem.builder()
                            .requirementText(textOrNull(node, "requirement_text"))
                            .originalRequirementNos(textOrNull(node, "original_requirement_nos"))
                            .clauseId(textOrNull(node, "clause_id"))
                            .pageNo(node.has("page_no") && !node.get("page_no").isNull() ? node.get("page_no").asInt() : null)
                            .sectionPath(textOrNull(node, "section_path"))
                            .requirementType(textOrDefault(node, "requirement_type", "ETC"))
                            .mandatory(node.has("mandatory") ? node.get("mandatory").asBoolean() : false)
                            .obligationType(textOrDefault(node, "obligation_type", "MUST"))
                            .sourceExcerpt(textOrNull(node, "source_excerpt"))
                            .build());
                }
            }
            return items;
        } catch (Exception e) {
            log.error("[AtomicSplit] 응답 파싱 실패: {}", e.getMessage());
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
    public static class AtomicItem {
        private String requirementText;
        private String originalRequirementNos;
        private String clauseId;
        private Integer pageNo;
        private String sectionPath;
        private String requirementType;
        private boolean mandatory;
        private String obligationType;
        private String sourceExcerpt;
    }

    @Getter @Builder
    public static class SplitResult {
        private List<AtomicItem> items;
        private String promptVersion;
        private String promptHash;
    }
}

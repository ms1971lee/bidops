package com.bidops.domain.requirement.dto;

import com.bidops.domain.document.entity.SourceExcerpt;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * GET /requirements/{requirementId}/sources 응답.
 *
 * pageRefs, clauseRefs 는 SourceExcerpt 레코드에서 동적으로 조합.
 * Requirement 엔티티에 직접 저장하지 않는다.
 */
@Getter
@Builder
public class RequirementSourcesDto {

    /** 연결된 SourceExcerpt 들의 pageNo 유니크 정렬 목록 */
    @JsonProperty("page_refs")
    private List<Integer> pageRefs;

    /** 연결된 SourceExcerpt 들의 anchorLabel 유니크 정렬 목록 */
    @JsonProperty("clause_refs")
    private List<String> clauseRefs;

    /** 원문 발췌 블록 전체 */
    @JsonProperty("source_text_blocks")
    private List<SourceExcerptDto> sourceTextBlocks;

    // ── factory ──────────────────────────────────────────────────────────────
    /**
     * SourceExcerpt 목록으로부터 pageRefs / clauseRefs 조합.
     */
    public static RequirementSourcesDto from(List<SourceExcerpt> excerpts) {
        List<Integer> pageRefs = excerpts.stream()
                .map(SourceExcerpt::getPageNo)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<String> clauseRefs = excerpts.stream()
                .map(SourceExcerpt::getAnchorLabel)
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<SourceExcerptDto> blocks = excerpts.stream()
                .map(SourceExcerptDto::from)
                .collect(Collectors.toList());

        return RequirementSourcesDto.builder()
                .pageRefs(pageRefs)
                .clauseRefs(clauseRefs)
                .sourceTextBlocks(blocks)
                .build();
    }

    // ── nested DTO ────────────────────────────────────────────────────────────
    @Getter
    @Builder
    public static class SourceExcerptDto {

        private String id;

        @JsonProperty("page_no")
        private Integer pageNo;

        @JsonProperty("excerpt_type")
        private SourceExcerpt.ExcerptType excerptType;

        @JsonProperty("anchor_label")
        private String anchorLabel;

        @JsonProperty("raw_text")
        private String rawText;

        @JsonProperty("normalized_text")
        private String normalizedText;

        /** bbox 좌표 JSON 문자열 그대로 반환 (클라이언트 파싱) */
        @JsonProperty("bbox_json")
        private String bboxJson;

        public static SourceExcerptDto from(SourceExcerpt e) {
            return SourceExcerptDto.builder()
                    .id(e.getId())
                    .pageNo(e.getPageNo())
                    .excerptType(e.getExcerptType())
                    .anchorLabel(e.getAnchorLabel())
                    .rawText(e.getRawText())
                    .normalizedText(e.getNormalizedText())
                    .bboxJson(e.getBboxJson())
                    .build();
        }
    }
}

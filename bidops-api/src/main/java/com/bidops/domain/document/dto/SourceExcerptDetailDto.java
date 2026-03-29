package com.bidops.domain.document.dto;

import com.bidops.domain.document.entity.SourceExcerpt;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * GET /source-excerpts/{id} 응답용 독립 DTO.
 *
 * RequirementSourcesDto.SourceExcerptDto 와 달리 document_id 를 포함하여
 * 프론트에서 PDF URL 해석에 사용할 수 있다.
 */
@Getter
@Builder
public class SourceExcerptDetailDto {

    private String id;

    @JsonProperty("document_id")
    private String documentId;

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

    @JsonProperty("bbox_json")
    private String bboxJson;

    public static SourceExcerptDetailDto from(SourceExcerpt e) {
        return SourceExcerptDetailDto.builder()
                .id(e.getId())
                .documentId(e.getDocumentId())
                .pageNo(e.getPageNo())
                .excerptType(e.getExcerptType())
                .anchorLabel(e.getAnchorLabel())
                .rawText(e.getRawText())
                .normalizedText(e.getNormalizedText())
                .bboxJson(e.getBboxJson())
                .build();
    }
}

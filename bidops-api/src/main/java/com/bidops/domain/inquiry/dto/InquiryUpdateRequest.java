package com.bidops.domain.inquiry.dto;

import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InquiryUpdateRequest {

    private String title;

    @JsonProperty("question_text")
    private String questionText;

    @JsonProperty("reason_note")
    private String reasonNote;

    private InquiryPriority priority;

    @JsonProperty("requirement_id")
    private String requirementId;

    @JsonProperty("source_excerpt_id")
    private String sourceExcerptId;
}

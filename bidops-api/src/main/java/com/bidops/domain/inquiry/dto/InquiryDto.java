package com.bidops.domain.inquiry.dto;

import com.bidops.domain.inquiry.entity.Inquiry;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.enums.InquiryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InquiryDto {

    private String id;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("inquiry_code")
    private String inquiryCode;

    private String title;

    @JsonProperty("question_text")
    private String questionText;

    @JsonProperty("reason_note")
    private String reasonNote;

    @JsonProperty("answer_text")
    private String answerText;

    private InquiryStatus status;
    private InquiryPriority priority;

    @JsonProperty("requirement_id")
    private String requirementId;

    @JsonProperty("source_excerpt_id")
    private String sourceExcerptId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static InquiryDto from(Inquiry i) {
        return InquiryDto.builder()
                .id(i.getId())
                .projectId(i.getProjectId())
                .inquiryCode(i.getInquiryCode())
                .title(i.getTitle())
                .questionText(i.getQuestionText())
                .reasonNote(i.getReasonNote())
                .answerText(i.getAnswerText())
                .status(i.getStatus())
                .priority(i.getPriority())
                .requirementId(i.getRequirementId())
                .sourceExcerptId(i.getSourceExcerptId())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}

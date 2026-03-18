package com.bidops.domain.inquiry.dto;

import com.bidops.domain.inquiry.enums.InquiryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InquiryStatusChangeRequest {

    @NotNull(message = "status는 필수입니다")
    private InquiryStatus status;

    /** ANSWERED 상태 전환 시 답변 내용 */
    @JsonProperty("answer_text")
    private String answerText;
}

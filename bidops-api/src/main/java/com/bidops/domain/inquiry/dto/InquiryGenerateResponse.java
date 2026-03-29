package com.bidops.domain.inquiry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class InquiryGenerateResponse {

    @JsonProperty("created_count")
    private final int createdCount;

    @JsonProperty("skipped_count")
    private final int skippedCount;

    @JsonProperty("created_inquiry_ids")
    private final List<String> createdInquiryIds;
}

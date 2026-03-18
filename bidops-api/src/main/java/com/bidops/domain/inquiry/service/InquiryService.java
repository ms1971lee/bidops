package com.bidops.domain.inquiry.service;

import com.bidops.domain.inquiry.dto.*;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.enums.InquiryStatus;

import java.util.List;

public interface InquiryService {

    List<InquiryDto> listInquiries(String projectId, InquiryStatus status, InquiryPriority priority, String requirementId);

    InquiryDto createInquiry(String projectId, InquiryCreateRequest request);

    InquiryDto getInquiry(String projectId, String inquiryId);

    InquiryDto updateInquiry(String projectId, String inquiryId, InquiryUpdateRequest request);

    InquiryDto changeStatus(String projectId, String inquiryId, InquiryStatusChangeRequest request);
}

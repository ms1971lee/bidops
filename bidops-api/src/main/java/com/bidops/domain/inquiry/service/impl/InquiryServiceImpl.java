package com.bidops.domain.inquiry.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.inquiry.dto.*;
import com.bidops.domain.inquiry.entity.Inquiry;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.enums.InquiryStatus;
import com.bidops.domain.inquiry.repository.InquiryRepository;
import com.bidops.domain.inquiry.service.InquiryService;
import com.bidops.domain.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryServiceImpl implements InquiryService {

    private final InquiryRepository inquiryRepository;
    private final ProjectService projectService;

    @Override
    public List<InquiryDto> listInquiries(String projectId, InquiryStatus status, InquiryPriority priority, String requirementId) {
        validateProject(projectId);
        return inquiryRepository.search(projectId, status, priority, requirementId)
                .stream().map(InquiryDto::from).toList();
    }

    @Override
    @Transactional
    public InquiryDto createInquiry(String projectId, InquiryCreateRequest request) {
        validateProject(projectId);

        long seq = inquiryRepository.countByProjectId(projectId) + 1;
        String code = String.format("INQ-%03d", seq);

        Inquiry inquiry = Inquiry.builder()
                .projectId(projectId)
                .inquiryCode(code)
                .title(request.getTitle())
                .questionText(request.getQuestionText())
                .reasonNote(request.getReasonNote())
                .priority(request.getPriority() != null ? request.getPriority() : InquiryPriority.MEDIUM)
                .requirementId(request.getRequirementId())
                .sourceExcerptId(request.getSourceExcerptId())
                .build();

        return InquiryDto.from(inquiryRepository.save(inquiry));
    }

    @Override
    public InquiryDto getInquiry(String projectId, String inquiryId) {
        return InquiryDto.from(findOrThrow(projectId, inquiryId));
    }

    @Override
    @Transactional
    public InquiryDto updateInquiry(String projectId, String inquiryId, InquiryUpdateRequest request) {
        Inquiry inquiry = findOrThrow(projectId, inquiryId);
        inquiry.update(request.getTitle(), request.getQuestionText(),
                       request.getReasonNote(), request.getPriority(),
                       request.getRequirementId(), request.getSourceExcerptId());
        return InquiryDto.from(inquiry);
    }

    @Override
    @Transactional
    public InquiryDto changeStatus(String projectId, String inquiryId, InquiryStatusChangeRequest request) {
        Inquiry inquiry = findOrThrow(projectId, inquiryId);

        if (request.getStatus() == InquiryStatus.ANSWERED && request.getAnswerText() != null) {
            inquiry.answer(request.getAnswerText());
        } else {
            inquiry.changeStatus(request.getStatus());
        }

        return InquiryDto.from(inquiry);
    }

    private Inquiry findOrThrow(String projectId, String inquiryId) {
        return inquiryRepository.findByIdAndProjectId(inquiryId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("질의"));
    }

    private void validateProject(String projectId) {
        projectService.validateAccess(com.bidops.auth.SecurityUtils.currentUserId(), projectId);
    }
}

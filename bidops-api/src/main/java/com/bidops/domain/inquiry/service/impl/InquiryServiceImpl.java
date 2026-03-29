package com.bidops.domain.inquiry.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.inquiry.dto.*;
import com.bidops.domain.inquiry.entity.Inquiry;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.enums.InquiryStatus;
import com.bidops.domain.inquiry.repository.InquiryRepository;
import com.bidops.domain.inquiry.service.InquiryService;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryServiceImpl implements InquiryService {

    private final InquiryRepository inquiryRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectActivityService activityService;

    @Override
    public List<InquiryDto> listInquiries(String projectId, InquiryStatus status, InquiryPriority priority, String requirementId) {
        requirePermission(projectId, ProjectPermission.INQUIRY_VIEW);
        return inquiryRepository.search(projectId, status, priority, requirementId)
                .stream().map(InquiryDto::from).toList();
    }

    @Override
    @Transactional
    public InquiryDto createInquiry(String projectId, InquiryCreateRequest request) {
        requirePermission(projectId, ProjectPermission.INQUIRY_EDIT);

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

        Inquiry saved = inquiryRepository.save(inquiry);
        activityService.record(projectId, ActivityType.INQUIRY_CREATED,
                "질의 생성: " + saved.getInquiryCode() + " - " + saved.getTitle(),
                com.bidops.auth.SecurityUtils.currentUserId(), saved.getId(), "inquiry", null);
        return InquiryDto.from(saved);
    }

    @Override
    public InquiryDto getInquiry(String projectId, String inquiryId) {
        requirePermission(projectId, ProjectPermission.INQUIRY_VIEW);
        return InquiryDto.from(findOrThrow(projectId, inquiryId));
    }

    @Override
    @Transactional
    public InquiryDto updateInquiry(String projectId, String inquiryId, InquiryUpdateRequest request) {
        requirePermission(projectId, ProjectPermission.INQUIRY_EDIT);
        Inquiry inquiry = findOrThrow(projectId, inquiryId);
        inquiry.update(request.getTitle(), request.getQuestionText(),
                       request.getReasonNote(), request.getPriority(),
                       request.getRequirementId(), request.getSourceExcerptId());
        activityService.record(projectId, ActivityType.INQUIRY_UPDATED,
                "질의 수정: " + inquiry.getInquiryCode(),
                com.bidops.auth.SecurityUtils.currentUserId(), inquiryId, "inquiry", null);
        return InquiryDto.from(inquiry);
    }

    @Override
    @Transactional
    public InquiryDto changeStatus(String projectId, String inquiryId, InquiryStatusChangeRequest request) {
        requirePermission(projectId, ProjectPermission.INQUIRY_EDIT);
        Inquiry inquiry = findOrThrow(projectId, inquiryId);

        if (request.getStatus() == InquiryStatus.ANSWERED && request.getAnswerText() != null) {
            inquiry.answer(request.getAnswerText());
        } else {
            inquiry.changeStatus(request.getStatus());
        }

        activityService.record(projectId, ActivityType.INQUIRY_STATUS_CHANGED,
                "질의 상태: " + inquiry.getInquiryCode() + " → " + request.getStatus(),
                com.bidops.auth.SecurityUtils.currentUserId(), inquiryId, "inquiry",
                request.getStatus().name());
        return InquiryDto.from(inquiry);
    }

    private Inquiry findOrThrow(String projectId, String inquiryId) {
        return inquiryRepository.findByIdAndProjectId(inquiryId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("질의"));
    }

    private void requirePermission(String projectId, ProjectPermission permission) {
        authorizationService.requirePermission(projectId, com.bidops.auth.SecurityUtils.currentUserId(), permission);
    }
}

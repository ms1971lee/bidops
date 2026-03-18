package com.bidops.domain.inquiry.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.enums.InquiryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * 발주처 질의 항목.
 * RFP 분석 중 모호/충돌/기준 불명확 항목을 질의서로 관리한다.
 * Requirement, SourceExcerpt와 선택적 연결 가능.
 */
@Entity
@Table(name = "inquiries", indexes = {
        @Index(name = "idx_inquiries_project",     columnList = "project_id"),
        @Index(name = "idx_inquiries_status",      columnList = "project_id, status"),
        @Index(name = "idx_inquiries_requirement", columnList = "requirement_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Inquiry extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    /** 질의 고유 코드 (예: INQ-001) */
    @Column(name = "inquiry_code", nullable = false, length = 30)
    private String inquiryCode;

    /** 질의 제목 */
    @Column(nullable = false, length = 300)
    private String title;

    /** 질의 본문 — 발주처에 보낼 질의 내용 */
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    /** 질의 배경/사유 — 왜 질의가 필요한지 내부 참고용 */
    @Column(name = "reason_note", columnDefinition = "TEXT")
    private String reasonNote;

    /** 발주처 답변 */
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InquiryStatus status = InquiryStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private InquiryPriority priority = InquiryPriority.MEDIUM;

    /** 관련 요구사항 (nullable) */
    @Column(name = "requirement_id", length = 36)
    private String requirementId;

    /** 원문 근거 (nullable) */
    @Column(name = "source_excerpt_id", length = 36)
    private String sourceExcerptId;

    // ── 변경 메서드 ────────────────────────────────────────────────────
    public void update(String title, String questionText, String reasonNote,
                       InquiryPriority priority, String requirementId,
                       String sourceExcerptId) {
        if (title != null)            this.title = title;
        if (questionText != null)     this.questionText = questionText;
        if (reasonNote != null)       this.reasonNote = reasonNote;
        if (priority != null)         this.priority = priority;
        if (requirementId != null)    this.requirementId = requirementId;
        if (sourceExcerptId != null)  this.sourceExcerptId = sourceExcerptId;
    }

    public void changeStatus(InquiryStatus newStatus) {
        this.status = newStatus;
    }

    public void answer(String answerText) {
        this.answerText = answerText;
        this.status = InquiryStatus.ANSWERED;
    }
}

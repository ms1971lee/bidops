package com.bidops.domain.requirement.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.requirement.enums.RequirementReviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 사람이 수행하는 검토 레이어.
 * RequirementInsight(AI 분석)와 명확히 분리하여 별도 테이블로 관리.
 *
 * 검토 이력이 필요할 경우 RequirementReviewHistory 테이블로 확장 가능.
 *
 * Requirement 와 1:1 관계 (requirement_id UNIQUE).
 */
@Entity
@Table(
    name = "requirement_reviews",
    indexes = @Index(name = "idx_req_reviews_req", columnList = "requirement_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class RequirementReview extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "requirement_id", nullable = false, unique = true, length = 36)
    private String requirementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    @Builder.Default
    private RequirementReviewStatus reviewStatus = RequirementReviewStatus.NOT_REVIEWED;

    /** 검토자 코멘트 (사람이 직접 작성) */
    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    /** 마지막 검토자 user ID */
    @Column(name = "reviewed_by_user_id", length = 36)
    private String reviewedByUserId;

    /** 마지막 검토 일시 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    // ── 변경 메서드 ────────────────────────────────────────────────────────────
    public void changeStatus(RequirementReviewStatus newStatus,
                             String comment,
                             String reviewerId) {
        this.reviewStatus     = newStatus;
        this.reviewComment    = comment;
        this.reviewedByUserId = reviewerId;
        this.reviewedAt       = LocalDateTime.now();
    }
}

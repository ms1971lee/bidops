package com.bidops.domain.checklist.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 체크리스트 항목 조치 이력.
 * 상태 변경, 담당자 변경, 메모 변경 시마다 1건씩 기록.
 */
@Entity
@Table(name = "checklist_reviews", indexes = {
        @Index(name = "idx_cl_review_item", columnList = "checklist_item_id, created_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ChecklistReview {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "checklist_item_id", nullable = false, length = 36)
    private String checklistItemId;

    /** 변경 유형: STATUS_CHANGED, OWNER_CHANGED, COMMENT_ADDED, UPDATED */
    @Column(name = "change_type", nullable = false, length = 30)
    private String changeType;

    @Column(name = "before_value", length = 200)
    private String beforeValue;

    @Column(name = "after_value", length = 200)
    private String afterValue;

    @Column(length = 1000)
    private String comment;

    @Column(name = "actor_user_id", nullable = false, length = 36)
    private String actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

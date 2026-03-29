package com.bidops.domain.checklist.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.bidops.domain.checklist.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * 체크리스트 상세 항목.
 * DB_ERD 4.15 ChecklistItem 기준.
 * 누락 방지가 핵심 — mandatoryFlag + riskLevel로 위험 항목 식별.
 */
@Entity
@Table(name = "checklist_items", indexes = {
        @Index(name = "idx_checklist_items_checklist", columnList = "checklist_id"),
        @Index(name = "idx_checklist_items_status",    columnList = "checklist_id, current_status"),
        @Index(name = "idx_checklist_items_risk",      columnList = "checklist_id, risk_level")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ChecklistItem extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "checklist_id", nullable = false, length = 36)
    private String checklistId;

    /** 원문 근거 연결 (nullable) */
    @Column(name = "source_excerpt_id", length = 36)
    private String sourceExcerptId;

    /** 관련 요구사항 연결 (nullable) */
    @Column(name = "linked_requirement_id", length = 36)
    private String linkedRequirementId;

    @Column(name = "item_code", nullable = false, length = 30)
    private String itemCode;

    @Column(name = "item_text", nullable = false, columnDefinition = "TEXT")
    private String itemText;

    @Column(name = "mandatory_flag", nullable = false)
    @Builder.Default
    private boolean mandatoryFlag = false;

    /** 기한 힌트 (예: "제출 마감 3일 전") */
    @Column(name = "due_hint", length = 200)
    private String dueHint;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 20)
    @Builder.Default
    private ChecklistItemStatus currentStatus = ChecklistItemStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 10)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.NONE;

    @Column(name = "risk_note", length = 500)
    private String riskNote;

    /** 담당자 user ID */
    @Column(name = "owner_user_id", length = 36)
    private String ownerUserId;

    /** 최근 조치/메모 코멘트 */
    @Column(name = "action_comment", length = 1000)
    private String actionComment;

    // ── 변경 메서드 ────────────────────────────────────────────────────────
    public void update(String itemText, Boolean mandatoryFlag, String dueHint,
                       RiskLevel riskLevel, String riskNote,
                       String linkedRequirementId) {
        if (itemText != null)              this.itemText = itemText;
        if (mandatoryFlag != null)         this.mandatoryFlag = mandatoryFlag;
        if (dueHint != null)               this.dueHint = dueHint;
        if (riskLevel != null)             this.riskLevel = riskLevel;
        if (riskNote != null)              this.riskNote = riskNote;
        if (linkedRequirementId != null)   this.linkedRequirementId = linkedRequirementId;
    }

    public void changeStatus(ChecklistItemStatus newStatus) {
        this.currentStatus = newStatus;
    }

    public void assignOwner(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public void setActionComment(String comment) {
        this.actionComment = comment;
    }
}

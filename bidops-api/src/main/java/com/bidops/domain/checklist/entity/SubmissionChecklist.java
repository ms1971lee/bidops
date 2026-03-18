package com.bidops.domain.checklist.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.checklist.enums.ChecklistType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * 프로젝트별 제출 체크리스트 묶음.
 * DB_ERD 4.14 SubmissionChecklist 기준.
 */
@Entity
@Table(name = "submission_checklists", indexes = {
        @Index(name = "idx_checklists_project", columnList = "project_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class SubmissionChecklist extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "checklist_type", nullable = false, length = 20)
    private ChecklistType checklistType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "created_by_job_id", length = 36)
    private String createdByJobId;

    public void updateTitle(String title) {
        if (title != null) this.title = title;
    }
}

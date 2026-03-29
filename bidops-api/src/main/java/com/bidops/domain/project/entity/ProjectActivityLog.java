package com.bidops.domain.project.entity;

import com.bidops.domain.project.enums.ActivityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 프로젝트 활동 이력 (감사로그).
 */
@Entity
@Table(name = "project_activity_logs",
        indexes = {
                @Index(name = "idx_pal_project_time", columnList = "project_id, created_at DESC"),
                @Index(name = "idx_pal_type", columnList = "activity_type"),
                @Index(name = "idx_pal_actor", columnList = "actor_user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ProjectActivityLog {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 40)
    private ActivityType activityType;

    /** 요약 메시지 (UI 표시용) */
    @Column(nullable = false, length = 500)
    private String summary;

    /** 활동 수행자 */
    @Column(name = "actor_user_id", nullable = false, length = 36)
    private String actorUserId;

    /** 대상 리소스 ID (요구사항, 문서, 체크리스트 등) */
    @Column(name = "target_id", length = 36)
    private String targetId;

    /** 대상 리소스 유형 (requirement, document, checklist, inquiry, member) */
    @Column(name = "target_type", length = 30)
    private String targetType;

    /** 추가 세부 정보 (JSON 또는 짧은 텍스트) */
    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

package com.bidops.domain.project.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 프로젝트 참여자 및 역할.
 * DB_ERD 4.4 ProjectMember 기준.
 */
@Entity
@Table(name = "project_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_project_member", columnNames = {"project_id", "user_id"}),
        indexes = {
                @Index(name = "idx_pm_project", columnList = "project_id"),
                @Index(name = "idx_pm_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ProjectMember {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_role", nullable = false, length = 20)
    @Builder.Default
    private ProjectRole projectRole = ProjectRole.EDITOR;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    public enum ProjectRole {
        OWNER, ADMIN, EDITOR, REVIEWER, VIEWER
    }
}

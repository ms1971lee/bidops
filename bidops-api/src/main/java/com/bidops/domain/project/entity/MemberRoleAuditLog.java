package com.bidops.domain.project.entity;

import com.bidops.domain.project.entity.ProjectMember.ProjectRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 멤버 역할 변경 감사로그.
 */
@Entity
@Table(name = "member_role_audit_logs",
        indexes = {
                @Index(name = "idx_mral_project", columnList = "project_id"),
                @Index(name = "idx_mral_target", columnList = "target_user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class MemberRoleAuditLog {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "target_user_id", nullable = false, length = 36)
    private String targetUserId;

    @Column(name = "changed_by", nullable = false, length = 36)
    private String changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_role", nullable = false, length = 20)
    private ProjectRole oldRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", nullable = false, length = 20)
    private ProjectRole newRole;

    @Column(name = "changed_at", nullable = false)
    @Builder.Default
    private LocalDateTime changedAt = LocalDateTime.now();
}

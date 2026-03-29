package com.bidops.domain.project.enums;

import com.bidops.domain.project.entity.ProjectMember.ProjectRole;

/**
 * 프로젝트 권한.
 * 각 권한이 요구하는 최소 역할(minimumRole)을 정의한다.
 * ordinal 순서: OWNER(0) > ADMIN(1) > EDITOR(2) > REVIEWER(3) > VIEWER(4)
 *
 * OWNER    : 전체 가능
 * ADMIN    : OWNER와 동일하나 멤버 관리는 OWNER만
 * EDITOR   : 조회 + 문서 업로드 + requirement/checklist/inquiry 수정
 * REVIEWER : 조회 + requirement 검토 상태 변경 (승인/보류/수정필요)
 * VIEWER   : 조회만
 */
public enum ProjectPermission {

    // ── 조회 (VIEWER 이상) ──
    PROJECT_VIEW(ProjectRole.VIEWER),
    DOCUMENT_VIEW(ProjectRole.VIEWER),
    REQUIREMENT_VIEW(ProjectRole.VIEWER),
    CHECKLIST_VIEW(ProjectRole.VIEWER),
    INQUIRY_VIEW(ProjectRole.VIEWER),
    ANALYSIS_VIEW(ProjectRole.VIEWER),
    MEMBER_VIEW(ProjectRole.VIEWER),

    // ── 검토 (REVIEWER 이상) ──
    REQUIREMENT_APPROVE(ProjectRole.REVIEWER),

    // ── 편집 (EDITOR 이상) ──
    DOCUMENT_UPLOAD(ProjectRole.EDITOR),
    REQUIREMENT_EDIT(ProjectRole.EDITOR),
    CHECKLIST_EDIT(ProjectRole.EDITOR),
    INQUIRY_EDIT(ProjectRole.EDITOR),

    // ── 관리 (ADMIN 이상) ──
    PROJECT_EDIT(ProjectRole.ADMIN),
    DOCUMENT_DELETE(ProjectRole.ADMIN),
    ANALYSIS_RUN(ProjectRole.ADMIN),

    // ── 소유자 (OWNER만) ──
    MEMBER_MANAGE(ProjectRole.OWNER);

    private final ProjectRole minimumRole;

    ProjectPermission(ProjectRole minimumRole) {
        this.minimumRole = minimumRole;
    }

    public ProjectRole getMinimumRole() {
        return minimumRole;
    }

    public boolean isSatisfiedBy(ProjectRole role) {
        return role.ordinal() <= minimumRole.ordinal();
    }
}

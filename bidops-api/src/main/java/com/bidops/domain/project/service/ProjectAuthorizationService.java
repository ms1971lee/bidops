package com.bidops.domain.project.service;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.util.SecurityUtils;
import com.bidops.domain.project.entity.Project;
import com.bidops.domain.project.entity.ProjectMember;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.repository.ProjectMemberRepository;
import com.bidops.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 프로젝트 권한 검증 공통 서비스.
 * 모든 도메인 서비스에서 requirePermission()을 호출하여 역할 기반 권한을 검증한다.
 */
@Service
@RequiredArgsConstructor
public class ProjectAuthorizationService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    /**
     * 프로젝트 멤버 여부 + 권한 검증.
     * 프로젝트 미존재 → 404, 멤버 아님 → 403, 권한 부족 → 403
     */
    public void requirePermission(String projectId, String userId, ProjectPermission permission) {
        Project project = projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> BidOpsException.notFound("프로젝트"));

        // 조직 격리: 프로젝트의 orgId와 현재 사용자의 orgId가 다르면 차단
        String currentOrgId = SecurityUtils.getCurrentOrganizationId();
        if (currentOrgId != null && project.getOrganizationId() != null
                && !currentOrgId.equals(project.getOrganizationId())) {
            throw BidOpsException.forbidden();
        }

        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(BidOpsException::forbidden);

        if (!permission.isSatisfiedBy(member.getProjectRole())) {
            throw BidOpsException.forbidden();
        }
    }

    /**
     * 멤버 여부만 확인 (기존 validateAccess 대체).
     * 최소 VIEWER 이상이면 통과.
     */
    public void requireMembership(String projectId, String userId) {
        requirePermission(projectId, userId, ProjectPermission.PROJECT_VIEW);
    }
}

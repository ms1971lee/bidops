package com.bidops.domain.project.service.impl;

import com.bidops.auth.UserRepository;
import com.bidops.auth.User;
import com.bidops.common.exception.BidOpsException;
import com.bidops.common.util.SecurityUtils;
import com.bidops.domain.project.dto.MemberRoleChangeRequest;
import com.bidops.domain.project.dto.ProjectMemberAddRequest;
import com.bidops.domain.project.dto.ProjectMemberDto;
import com.bidops.domain.project.entity.MemberRoleAuditLog;
import com.bidops.domain.project.entity.ProjectMember;
import com.bidops.domain.project.entity.ProjectMember.ProjectRole;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.repository.MemberRoleAuditLogRepository;
import com.bidops.domain.project.repository.ProjectMemberRepository;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import com.bidops.domain.project.service.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectAuthorizationService authorizationService;
    private final MemberRoleAuditLogRepository auditLogRepository;
    private final ProjectActivityService activityService;

    @Override
    public List<ProjectMemberDto> listMembers(String currentUserId, String projectId) {
        authorizationService.requirePermission(projectId, currentUserId, ProjectPermission.MEMBER_VIEW);

        List<ProjectMember> members = memberRepository.findByProjectId(projectId);
        List<String> userIds = members.stream().map(ProjectMember::getUserId).toList();
        Map<String, User> userMap = userRepository.findAllById(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        return members.stream()
                .map(m -> {
                    User u = userMap.get(m.getUserId());
                    return ProjectMemberDto.from(m,
                            u != null ? u.getEmail() : null,
                            u != null ? u.getName() : null);
                })
                .toList();
    }

    @Override
    @Transactional
    public ProjectMemberDto addMember(String currentUserId, String projectId, ProjectMemberAddRequest request) {
        authorizationService.requirePermission(projectId, currentUserId, ProjectPermission.MEMBER_MANAGE);

        User targetUser = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> BidOpsException.notFound("사용자 (" + request.getEmail() + ")"));

        // 같은 조직 소속인지 검증
        String currentOrgId = SecurityUtils.getCurrentOrganizationId();
        if (currentOrgId != null && !currentOrgId.equals(targetUser.getOrganizationId())) {
            throw BidOpsException.badRequest("같은 조직의 사용자만 추가할 수 있습니다.");
        }

        if (memberRepository.existsByProjectIdAndUserId(projectId, targetUser.getId())) {
            throw BidOpsException.conflict("이미 프로젝트 멤버입니다.");
        }

        ProjectMember.ProjectRole role = request.getProjectRole() != null
                ? request.getProjectRole()
                : ProjectMember.ProjectRole.EDITOR;

        ProjectMember member = ProjectMember.builder()
                .projectId(projectId)
                .userId(targetUser.getId())
                .projectRole(role)
                .build();
        memberRepository.save(member);

        activityService.record(projectId, ActivityType.MEMBER_ADDED,
                "멤버 추가: " + targetUser.getName() + " (" + role + ")",
                currentUserId, targetUser.getId(), "member", null);

        return ProjectMemberDto.from(member, targetUser.getEmail(), targetUser.getName());
    }

    @Override
    @Transactional
    public void removeMember(String currentUserId, String projectId, String targetUserId) {
        authorizationService.requirePermission(projectId, currentUserId, ProjectPermission.MEMBER_MANAGE);

        if (currentUserId.equals(targetUserId)) {
            throw BidOpsException.badRequest("OWNER 자신은 제거할 수 없습니다.");
        }

        if (!memberRepository.existsByProjectIdAndUserId(projectId, targetUserId)) {
            throw BidOpsException.notFound("멤버");
        }

        memberRepository.deleteByProjectIdAndUserId(projectId, targetUserId);

        activityService.record(projectId, ActivityType.MEMBER_REMOVED,
                "멤버 제거: " + targetUserId,
                currentUserId, targetUserId, "member", null);
    }

    @Override
    @Transactional
    public ProjectMemberDto changeRole(String currentUserId, String projectId,
                                        String memberId, MemberRoleChangeRequest request) {
        authorizationService.requirePermission(projectId, currentUserId, ProjectPermission.MEMBER_MANAGE);

        ProjectMember target = memberRepository.findById(memberId)
                .filter(m -> m.getProjectId().equals(projectId))
                .orElseThrow(() -> BidOpsException.notFound("멤버"));

        if (target.getUserId().equals(currentUserId)) {
            throw BidOpsException.badRequest("자기 자신의 역할은 변경할 수 없습니다.");
        }

        ProjectRole oldRole = target.getProjectRole();
        ProjectRole newRole = request.getRole();

        if (oldRole == newRole) {
            throw BidOpsException.badRequest("현재와 동일한 역할입니다.");
        }

        // 마지막 OWNER 강등 방지
        if (oldRole == ProjectRole.OWNER && newRole != ProjectRole.OWNER) {
            long ownerCount = memberRepository.countByProjectIdAndProjectRole(projectId, ProjectRole.OWNER);
            if (ownerCount <= 1) {
                throw BidOpsException.badRequest("프로젝트에 최소 1명의 OWNER가 필요합니다.");
            }
        }

        target.changeRole(newRole);

        auditLogRepository.save(MemberRoleAuditLog.builder()
                .projectId(projectId)
                .targetUserId(target.getUserId())
                .changedBy(currentUserId)
                .oldRole(oldRole)
                .newRole(newRole)
                .build());

        activityService.record(projectId, ActivityType.MEMBER_ROLE_CHANGED,
                "역할 변경: " + oldRole + " → " + newRole,
                currentUserId, target.getUserId(), "member",
                oldRole + " → " + newRole);

        User user = userRepository.findById(target.getUserId()).orElse(null);
        return ProjectMemberDto.from(target,
                user != null ? user.getEmail() : null,
                user != null ? user.getName() : null);
    }
}

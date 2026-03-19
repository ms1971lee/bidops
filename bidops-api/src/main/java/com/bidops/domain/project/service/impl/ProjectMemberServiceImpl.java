package com.bidops.domain.project.service.impl;

import com.bidops.auth.UserRepository;
import com.bidops.auth.User;
import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.project.dto.ProjectMemberAddRequest;
import com.bidops.domain.project.dto.ProjectMemberDto;
import com.bidops.domain.project.entity.ProjectMember;
import com.bidops.domain.project.repository.ProjectMemberRepository;
import com.bidops.domain.project.service.ProjectMemberService;
import com.bidops.domain.project.service.ProjectService;
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
    private final ProjectService projectService;

    @Override
    public List<ProjectMemberDto> listMembers(String currentUserId, String projectId) {
        projectService.validateAccess(currentUserId, projectId);

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
        requireOwner(currentUserId, projectId);

        User targetUser = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> BidOpsException.notFound("사용자 (" + request.getEmail() + ")"));

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

        return ProjectMemberDto.from(member, targetUser.getEmail(), targetUser.getName());
    }

    @Override
    @Transactional
    public void removeMember(String currentUserId, String projectId, String targetUserId) {
        requireOwner(currentUserId, projectId);

        if (currentUserId.equals(targetUserId)) {
            throw BidOpsException.badRequest("OWNER 자신은 제거할 수 없습니다.");
        }

        if (!memberRepository.existsByProjectIdAndUserId(projectId, targetUserId)) {
            throw BidOpsException.notFound("멤버");
        }

        memberRepository.deleteByProjectIdAndUserId(projectId, targetUserId);
    }

    private void requireOwner(String userId, String projectId) {
        projectService.validateAccess(userId, projectId);
        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> BidOpsException.forbidden());
        if (member.getProjectRole() != ProjectMember.ProjectRole.OWNER) {
            throw BidOpsException.forbidden();
        }
    }
}

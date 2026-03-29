package com.bidops.domain.project.service;

import com.bidops.domain.project.dto.MemberRoleChangeRequest;
import com.bidops.domain.project.dto.ProjectMemberAddRequest;
import com.bidops.domain.project.dto.ProjectMemberDto;

import java.util.List;

public interface ProjectMemberService {

    List<ProjectMemberDto> listMembers(String currentUserId, String projectId);

    ProjectMemberDto addMember(String currentUserId, String projectId, ProjectMemberAddRequest request);

    void removeMember(String currentUserId, String projectId, String targetUserId);

    ProjectMemberDto changeRole(String currentUserId, String projectId, String memberId, MemberRoleChangeRequest request);
}

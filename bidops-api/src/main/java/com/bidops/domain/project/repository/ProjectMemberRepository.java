package com.bidops.domain.project.repository;

import com.bidops.domain.project.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, String> {

    boolean existsByProjectIdAndUserId(String projectId, String userId);

    List<ProjectMember> findByUserId(String userId);

    List<ProjectMember> findByProjectId(String projectId);
}

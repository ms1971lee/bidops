package com.bidops.domain.project.repository;

import com.bidops.domain.project.entity.MemberRoleAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberRoleAuditLogRepository extends JpaRepository<MemberRoleAuditLog, String> {

    List<MemberRoleAuditLog> findByProjectIdOrderByChangedAtDesc(String projectId);
}

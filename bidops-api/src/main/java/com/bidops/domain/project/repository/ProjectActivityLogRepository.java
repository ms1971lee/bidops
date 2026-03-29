package com.bidops.domain.project.repository;

import com.bidops.domain.project.entity.ProjectActivityLog;
import com.bidops.domain.project.enums.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProjectActivityLogRepository extends JpaRepository<ProjectActivityLog, String> {

    @Query("""
            SELECT a FROM ProjectActivityLog a
            WHERE a.projectId = :projectId
              AND (:activityType IS NULL OR a.activityType = :activityType)
              AND (:targetType IS NULL OR a.targetType = :targetType)
              AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
              AND (:dateFrom IS NULL OR a.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR a.createdAt <= :dateTo)
            ORDER BY a.createdAt DESC
            """)
    Page<ProjectActivityLog> search(
            @Param("projectId") String projectId,
            @Param("activityType") ActivityType activityType,
            @Param("targetType") String targetType,
            @Param("actorUserId") String actorUserId,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);
}

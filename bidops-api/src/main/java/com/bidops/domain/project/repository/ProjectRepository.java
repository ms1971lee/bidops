package com.bidops.domain.project.repository;

import com.bidops.domain.project.entity.Project;
import com.bidops.domain.project.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, String> {

    @Query("""
            SELECT p FROM Project p
            WHERE p.deleted = false
              AND (:keyword IS NULL OR p.name LIKE %:keyword% OR p.clientName LIKE %:keyword%)
              AND (:status  IS NULL OR p.status = :status)
            """)
    Page<Project> search(
            @Param("keyword") String keyword,
            @Param("status")  ProjectStatus status,
            Pageable pageable);

    Optional<Project> findByIdAndDeletedFalse(String id);
}

package com.bidops.domain.inquiry.repository;

import com.bidops.domain.inquiry.entity.Inquiry;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.enums.InquiryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InquiryRepository extends JpaRepository<Inquiry, String> {

    @Query("""
            SELECT i FROM Inquiry i
            WHERE i.projectId = :projectId
              AND (:status        IS NULL OR i.status        = :status)
              AND (:priority      IS NULL OR i.priority       = :priority)
              AND (:requirementId IS NULL OR i.requirementId  = :requirementId)
            ORDER BY i.createdAt DESC
            """)
    List<Inquiry> search(
            @Param("projectId")     String projectId,
            @Param("status")        InquiryStatus status,
            @Param("priority")      InquiryPriority priority,
            @Param("requirementId") String requirementId);

    Optional<Inquiry> findByIdAndProjectId(String id, String projectId);

    long countByProjectId(String projectId);

    boolean existsByProjectIdAndRequirementId(String projectId, String requirementId);
}

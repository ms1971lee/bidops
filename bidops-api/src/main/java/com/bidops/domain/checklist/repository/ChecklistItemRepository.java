package com.bidops.domain.checklist.repository;

import com.bidops.domain.checklist.entity.ChecklistItem;
import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.bidops.domain.checklist.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, String> {

    @Query("""
            SELECT i FROM ChecklistItem i
            WHERE i.checklistId = :checklistId
              AND (:status        IS NULL OR i.currentStatus        = :status)
              AND (:riskLevel     IS NULL OR i.riskLevel            = :riskLevel)
              AND (:mandatory     IS NULL OR i.mandatoryFlag        = :mandatory)
              AND (:requirementId IS NULL OR i.linkedRequirementId  = :requirementId)
              AND (:ownerUserId   IS NULL OR i.ownerUserId          = :ownerUserId)
              AND (:keyword       IS NULL OR LOWER(i.itemText) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY i.itemCode ASC
            """)
    List<ChecklistItem> search(
            @Param("checklistId")   String checklistId,
            @Param("status")        ChecklistItemStatus status,
            @Param("riskLevel")     RiskLevel riskLevel,
            @Param("mandatory")     Boolean mandatory,
            @Param("requirementId") String requirementId,
            @Param("ownerUserId")   String ownerUserId,
            @Param("keyword")       String keyword);

    Optional<ChecklistItem> findByIdAndChecklistId(String id, String checklistId);

    long countByChecklistId(String checklistId);

    long countByChecklistIdAndCurrentStatus(String checklistId, ChecklistItemStatus status);
}

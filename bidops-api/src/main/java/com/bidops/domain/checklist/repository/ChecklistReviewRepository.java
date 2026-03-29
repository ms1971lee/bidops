package com.bidops.domain.checklist.repository;

import com.bidops.domain.checklist.entity.ChecklistReview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistReviewRepository extends JpaRepository<ChecklistReview, String> {

    List<ChecklistReview> findByChecklistItemIdOrderByCreatedAtDesc(String checklistItemId);

    List<ChecklistReview> findByChecklistItemIdOrderByCreatedAtDesc(String checklistItemId, Pageable pageable);
}

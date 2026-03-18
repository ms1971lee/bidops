package com.bidops.domain.requirement.repository;

import com.bidops.domain.requirement.entity.RequirementReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequirementReviewRepository extends JpaRepository<RequirementReview, String> {

    Optional<RequirementReview> findByRequirementId(String requirementId);
}

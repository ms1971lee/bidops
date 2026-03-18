package com.bidops.domain.requirement.repository;

import com.bidops.domain.requirement.entity.RequirementInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequirementInsightRepository extends JpaRepository<RequirementInsight, String> {

    Optional<RequirementInsight> findByRequirementId(String requirementId);
}

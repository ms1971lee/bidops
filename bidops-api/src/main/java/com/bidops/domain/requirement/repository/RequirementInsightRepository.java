package com.bidops.domain.requirement.repository;

import com.bidops.domain.requirement.entity.RequirementInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RequirementInsightRepository extends JpaRepository<RequirementInsight, String> {

    Optional<RequirementInsight> findByRequirementId(String requirementId);

    @Modifying @Transactional
    void deleteByRequirementId(String requirementId);
}

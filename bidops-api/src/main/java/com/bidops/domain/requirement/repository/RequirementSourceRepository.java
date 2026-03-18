package com.bidops.domain.requirement.repository;

import com.bidops.domain.requirement.entity.RequirementSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequirementSourceRepository extends JpaRepository<RequirementSource, String> {

    List<RequirementSource> findByRequirementIdOrderByLinkTypeAsc(String requirementId);

    void deleteByRequirementIdAndSourceExcerptId(String requirementId, String sourceExcerptId);
}

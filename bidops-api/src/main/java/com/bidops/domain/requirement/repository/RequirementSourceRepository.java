package com.bidops.domain.requirement.repository;

import com.bidops.domain.requirement.entity.RequirementSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RequirementSourceRepository extends JpaRepository<RequirementSource, String> {

    List<RequirementSource> findByRequirementIdOrderByLinkTypeAsc(String requirementId);

    @Modifying @Transactional
    void deleteByRequirementIdAndSourceExcerptId(String requirementId, String sourceExcerptId);

    @Modifying @Transactional
    void deleteByRequirementId(String requirementId);
}

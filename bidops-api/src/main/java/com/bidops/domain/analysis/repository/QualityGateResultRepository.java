package com.bidops.domain.analysis.repository;

import com.bidops.domain.analysis.entity.QualityGateResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QualityGateResultRepository extends JpaRepository<QualityGateResult, String> {

    Optional<QualityGateResult> findByRequirementId(String requirementId);

    long countByAnalysisJobIdAndGateStatus(String analysisJobId, String gateStatus);
}

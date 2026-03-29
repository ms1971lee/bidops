package com.bidops.domain.analysis.repository;

import com.bidops.domain.analysis.entity.CoverageAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoverageAuditRepository extends JpaRepository<CoverageAudit, String> {

    Optional<CoverageAudit> findTopByDocumentIdOrderByCreatedAtDesc(String documentId);

    Optional<CoverageAudit> findByAnalysisJobId(String analysisJobId);
}

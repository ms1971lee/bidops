-- V006__analysis_rebuild.sql
-- BidOps AI 분석 엔진 전면 재설계: 엔티티 확장 + 신규 테이블

-- ═══ 1. requirements 확장 ═══
ALTER TABLE requirements ADD COLUMN source_clause_id VARCHAR(50);
ALTER TABLE requirements ADD COLUMN atomic_flag BOOLEAN DEFAULT TRUE;
ALTER TABLE requirements ADD COLUMN extraction_status_v2 VARCHAR(20) DEFAULT 'DETECTED';
ALTER TABLE requirements ADD COLUMN enrichment_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE requirements ADD COLUMN quality_gate_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE requirements ADD COLUMN analysis_job_version INT DEFAULT 1;
ALTER TABLE requirements ADD COLUMN archived BOOLEAN DEFAULT FALSE;
ALTER TABLE requirements ADD COLUMN visible BOOLEAN DEFAULT TRUE;
-- prompt_version은 requirements에 두지 않음 (insight/qg_result에서 관리)

-- 기존 데이터 마이그레이션 (이미 분석 완료된 데이터)
UPDATE requirements SET extraction_status_v2 = 'SPLIT' WHERE extraction_status_v2 = 'DETECTED';
UPDATE requirements SET enrichment_status = 'ENRICHED'
  WHERE enrichment_status = 'PENDING'
    AND id IN (SELECT requirement_id FROM requirement_insights WHERE fact_summary IS NOT NULL);
UPDATE requirements SET quality_gate_status = 'PASS' WHERE quality_gate_status = 'PENDING';
UPDATE requirements SET archived = FALSE WHERE archived IS NULL;
UPDATE requirements SET visible = TRUE WHERE visible IS NULL;

-- ═══ 2. requirement_insights 확장 ═══
ALTER TABLE requirement_insights ADD COLUMN split_prompt_version VARCHAR(50);
ALTER TABLE requirement_insights ADD COLUMN analysis_prompt_version VARCHAR(50);

-- ═══ 3. analysis_jobs 확장 ═══
ALTER TABLE analysis_jobs ADD COLUMN split_prompt_version VARCHAR(50);
ALTER TABLE analysis_jobs ADD COLUMN analysis_prompt_version VARCHAR(50);
ALTER TABLE analysis_jobs ADD COLUMN quality_gate_version VARCHAR(50);
ALTER TABLE analysis_jobs ADD COLUMN split_prompt_hash VARCHAR(64);
ALTER TABLE analysis_jobs ADD COLUMN analysis_prompt_hash VARCHAR(64);
ALTER TABLE analysis_jobs ADD COLUMN quality_gate_hash VARCHAR(64);

-- ═══ 4. coverage_audits 확장 ═══
ALTER TABLE coverage_audits ADD COLUMN expected_original_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN detected_original_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN ai_extracted_original_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN saved_original_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN save_failed_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN qg_failed_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN visible_count INT;
ALTER TABLE coverage_audits ADD COLUMN visible_original_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN hidden_after_query_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN final_missing_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN merged_original_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN merged_out_nos TEXT;
ALTER TABLE coverage_audits ADD COLUMN missing_after_detection TEXT;
ALTER TABLE coverage_audits ADD COLUMN missing_after_ai TEXT;
ALTER TABLE coverage_audits ADD COLUMN merge_details TEXT;

-- ═══ 5. quality_gate_results 신규 ═══
CREATE TABLE IF NOT EXISTS quality_gate_results (
  id VARCHAR(36) PRIMARY KEY,
  requirement_id VARCHAR(36) NOT NULL,
  analysis_job_id VARCHAR(36),
  gate_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  total_checks INT DEFAULT 0,
  passed_checks INT DEFAULT 0,
  failed_checks INT DEFAULT 0,
  failure_reasons TEXT,
  check_details TEXT,
  prompt_version VARCHAR(50),
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(36),
  updated_at DATETIME(6) NOT NULL,
  updated_by VARCHAR(36)
);
CREATE INDEX idx_qgr_requirement ON quality_gate_results(requirement_id);
CREATE INDEX idx_qgr_job ON quality_gate_results(analysis_job_id);

-- ═══ 6. analysis_issues 신규 ═══
CREATE TABLE IF NOT EXISTS analysis_issues (
  id VARCHAR(36) PRIMARY KEY,
  project_id VARCHAR(36) NOT NULL,
  document_id VARCHAR(36) NOT NULL,
  analysis_job_id VARCHAR(36),
  requirement_id VARCHAR(36) NULL,
  original_req_no VARCHAR(50),
  clause_id VARCHAR(50),
  page_no INT,
  source_excerpt TEXT,
  issue_type VARCHAR(30) NOT NULL,
  failure_reason TEXT,
  raw_ai_output TEXT,
  resolution_status VARCHAR(20) DEFAULT 'OPEN',
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(36),
  updated_at DATETIME(6) NOT NULL,
  updated_by VARCHAR(36)
);
CREATE INDEX idx_ai_project ON analysis_issues(project_id);
CREATE INDEX idx_ai_job ON analysis_issues(analysis_job_id);
CREATE INDEX idx_ai_requirement ON analysis_issues(requirement_id);

-- ═══ 7. 인덱스 ═══
CREATE INDEX idx_req_visible ON requirements(visible);
CREATE INDEX idx_req_archived ON requirements(archived);
CREATE INDEX idx_req_extraction_v2 ON requirements(extraction_status_v2);
CREATE INDEX idx_req_enrichment ON requirements(enrichment_status);
CREATE INDEX idx_req_qg ON requirements(quality_gate_status);

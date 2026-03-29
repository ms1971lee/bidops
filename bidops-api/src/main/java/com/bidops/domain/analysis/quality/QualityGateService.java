package com.bidops.domain.analysis.quality;

import com.bidops.domain.analysis.entity.QualityGateResult;
import com.bidops.domain.analysis.repository.QualityGateResultRepository;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 품질 게이트 서비스.
 * RequirementInsight의 분석 결과를 검증하고 QualityGateResult를 생성한다.
 *
 * 검증 규칙:
 * 1. interpretation 빈값 금지
 * 2. proposal_point generic 차단
 * 3. implementation_approach How 요소 3개 이상
 * 4. differentiation_point 구체 장치 포함
 * 5. fact_basis / inference_note 혼입 방지
 * 6. query_needed / risk_note 최소 1개
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityGateService {

    private final GenericPhraseFilter genericFilter;
    private final QualityGateResultRepository repository;
    private final ObjectMapper objectMapper;

    public static final String VERSION = "qg_v1";

    /**
     * RequirementInsight를 검증하고 QualityGateResult를 반환.
     * DB에도 저장한다.
     */
    public QualityGateResult evaluate(RequirementInsight insight, String analysisJobId) {
        List<CheckItem> checks = new ArrayList<>();

        // Rule 1: interpretation/intent 빈값 금지
        checks.add(checkNotBlank("interpretation_not_blank",
                insight.getInterpretationSummary(),
                "interpretationSummary(AI 해석)가 비어있습니다"));

        // Rule 2: proposal_point generic 차단
        checks.add(checkNotGeneric("proposal_point_not_generic",
                insight.getProposalPoint(),
                "proposalPoint에 generic 문장만 있고 구체 수단이 없습니다"));

        // Rule 3: implementation_approach How 요소 (빈값이면 WARN, 내용 있으면 How 체크)
        String implText = insight.getImplementationApproach();
        if (implText == null || implText.isBlank()) {
            checks.add(new CheckItem("implementation_how_elements", "WARN",
                    "implementationApproach가 비어있습니다 (enrichment 파싱 확인 필요)"));
        } else {
            int howCount = genericFilter.countHowElements(implText);
            checks.add(new CheckItem("implementation_how_elements",
                    howCount >= 3 ? "PASS" : howCount >= 1 ? "WARN" : "FAIL",
                    "implementationApproach How 요소: " + howCount + "/4 (주체/절차/주기/산출물)"));
        }

        // Rule 4: differentiation_point 구체 장치 포함
        boolean hasConcrete = genericFilter.hasConcreteDevice(insight.getDifferentiationPoint());
        checks.add(new CheckItem("differentiation_concrete",
                hasConcrete ? "PASS" : "WARN",
                hasConcrete ? "구체 장치 포함" : "differentiationPoint에 KPI/도구/거버넌스 등 구체 장치 없음"));

        // Rule 5: fact_basis 존재 확인
        checks.add(checkNotBlank("fact_basis_exists",
                insight.getFactSummary(),
                "factSummary(사실 근거)가 비어있습니다"));

        // Rule 6: query_needed / risk_note 최소 1개
        boolean hasQueryOrRisk = insight.isQueryNeeded()
                || (insight.getRiskNoteJson() != null && !insight.getRiskNoteJson().isBlank()
                    && !"[]".equals(insight.getRiskNoteJson().trim()));
        checks.add(new CheckItem("query_or_risk_exists",
                hasQueryOrRisk ? "PASS" : "WARN",
                hasQueryOrRisk ? "질의/리스크 정보 있음" : "queryNeeded와 riskNote 둘 다 비어있습니다"));

        // 집계
        int totalChecks = checks.size();
        int passedChecks = (int) checks.stream().filter(c -> "PASS".equals(c.status)).count();
        int failedChecks = (int) checks.stream().filter(c -> "FAIL".equals(c.status)).count();
        int warnChecks = totalChecks - passedChecks - failedChecks;

        String gateStatus = failedChecks > 0 ? "FAIL" : warnChecks > 0 ? "WARN" : "PASS";

        List<String> failureReasons = checks.stream()
                .filter(c -> "FAIL".equals(c.status))
                .map(c -> c.message)
                .toList();

        String checkDetailsJson = null;
        String failureReasonsJson = null;
        try {
            checkDetailsJson = objectMapper.writeValueAsString(checks);
            if (!failureReasons.isEmpty()) {
                failureReasonsJson = objectMapper.writeValueAsString(failureReasons);
            }
        } catch (Exception e) {
            log.warn("[QualityGate] JSON 직렬화 실패", e);
        }

        QualityGateResult result = QualityGateResult.builder()
                .requirementId(insight.getRequirementId())
                .analysisJobId(analysisJobId)
                .gateStatus(gateStatus)
                .totalChecks(totalChecks)
                .passedChecks(passedChecks)
                .failedChecks(failedChecks)
                .failureReasons(failureReasonsJson)
                .checkDetails(checkDetailsJson)
                .promptVersion(VERSION)
                .build();

        repository.save(result);
        return result;
    }

    private CheckItem checkNotBlank(String rule, String value, String failMessage) {
        boolean pass = value != null && !value.isBlank();
        return new CheckItem(rule, pass ? "PASS" : "FAIL", pass ? "값 존재" : failMessage);
    }

    private CheckItem checkNotGeneric(String rule, String value, String failMessage) {
        if (value == null || value.isBlank()) {
            return new CheckItem(rule, "FAIL", "값이 비어있습니다");
        }
        boolean isGeneric = genericFilter.isGenericOnly(value);
        return new CheckItem(rule, isGeneric ? "FAIL" : "PASS", isGeneric ? failMessage : "구체적 내용 포함");
    }

    public record CheckItem(String rule, String status, String message) {}
}

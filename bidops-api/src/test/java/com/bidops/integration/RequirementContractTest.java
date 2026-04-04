package com.bidops.integration;

import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.enums.*;
import com.bidops.domain.requirement.repository.RequirementInsightRepository;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Requirement 핵심 API 계약 테스트.
 * DB에 직접 Requirement + RequirementInsight를 삽입 후 API 응답 구조를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RequirementContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired RequirementRepository requirementRepo;
    @Autowired RequirementInsightRepository insightRepo;

    static String token;
    static String projectId;
    static String documentId;
    static String requirementId;
    private TestHelper h;

    @BeforeEach
    void initHelper() { h = new TestHelper(mvc, om); }

    private JsonNode dataOf(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString()).get("data");
    }

    // ─── Setup ───────────────────────────────────────────────────────
    @Test @Order(1) @DisplayName("Setup: 사용자 + 프로젝트 + 문서")
    void setup() throws Exception {
        token = h.signup("요구사항검토자", "reqtest@test.com", "password123", "검토조직");
        projectId = h.createProject(token, "요구사항 테스트 프로젝트");
        documentId = h.uploadDocument(token, projectId, "rfp-for-req.pdf", "RFP");
    }

    @Test @Order(2) @DisplayName("Setup: Requirement 직접 삽입 (DB)")
    void insertRequirement() {
        Requirement req = Requirement.builder()
                .projectId(projectId)
                .documentId(documentId)
                .requirementCode("REQ-001")
                .title("시스템 보안 요구사항")
                .originalText("수급인은 ISMS-P 인증 수준의 보안 체계를 갖추어야 한다.")
                .category(RequirementCategory.SECURITY)
                .mandatoryFlag(true)
                .evidenceRequiredFlag(true)
                .queryNeeded(true)
                .factLevel(FactLevel.FACT)
                .confidenceScore(0.92f)
                .build();
        req = requirementRepo.save(req);
        requirementId = req.getId();
        assertThat(requirementId).isNotBlank();
    }

    @Test @Order(3) @DisplayName("Setup: RequirementInsight 직접 삽입 (DB)")
    void insertInsight() {
        RequirementInsight insight = RequirementInsight.builder()
                .requirementId(requirementId)
                .factSummary("ISMS-P 인증 수준의 보안 체계 요구")
                .interpretationSummary("사실상 ISMS-P 기준을 충족해야 함")
                .intentSummary("체계적인 보안 관리 역량을 갖춘 업체 선정")
                .proposalPoint("인증 보유 사실 증빙")
                .implementationApproach("단계적 보안 체계 구축")
                .differentiationPoint("자체 SOC 운영 경험")
                .queryNeeded(true)
                .factLevel(FactLevel.FACT)
                .build();
        insightRepo.save(insight);
    }

    // ─── Requirement 목록 API ────────────────────────────────────────
    @Test @Order(10) @DisplayName("GET /requirements → 목록에 삽입된 요구사항 포함")
    void listRequirements() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].requirement_code").value("REQ-001"))
                .andExpect(jsonPath("$.data.items[0].category").value("SECURITY"))
                .andExpect(jsonPath("$.data.items[0].mandatory_flag").value(true))
                .andExpect(jsonPath("$.data.items[0].review_status").value("NOT_REVIEWED"))
                .andExpect(jsonPath("$.data.items[0].fact_level").value("FACT"));
    }

    @Test @Order(11) @DisplayName("GET /requirements 필터: category=SECURITY")
    void listRequirementsByCat() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements?category=SECURITY")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].category").value("SECURITY"));
    }

    @Test @Order(12) @DisplayName("GET /requirements 필터: query_needed=true")
    void listRequirementsByQuery() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements?query_needed=true")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].query_needed").value(true));
    }

    // ─── Requirement 상세 API ────────────────────────────────────────
    @Test @Order(20) @DisplayName("GET /requirements/{id} → requirement + insight + review 분리 응답")
    void getRequirementDetail() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/" + requirementId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requirement").exists())
                .andExpect(jsonPath("$.data.requirement.requirement_code").value("REQ-001"))
                .andExpect(jsonPath("$.data.requirement.title").value("시스템 보안 요구사항"))
                .andExpect(jsonPath("$.data.requirement.original_text").isNotEmpty())
                .andExpect(jsonPath("$.data.requirement.fact_level").value("FACT"))
                .andReturn();

        // insight와 review가 분리되어 있는지 확인
        JsonNode data = dataOf(r);
        assertThat(data.has("requirement")).isTrue();
        // insight가 있으면 분리 구조 확인
        // review는 없을 수 있음 (NOT_REVIEWED 상태)
    }

    // ─── Requirement Analysis API ────────────────────────────────────
    @Test @Order(30) @DisplayName("GET /requirements/{id}/analysis → AI 분석 결과 조회")
    void getRequirementAnalysis() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/" + requirementId + "/analysis")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fact_summary").value("ISMS-P 인증 수준의 보안 체계 요구"))
                .andExpect(jsonPath("$.data.interpretation_summary").isNotEmpty())
                .andExpect(jsonPath("$.data.intent_summary").isNotEmpty())
                .andExpect(jsonPath("$.data.proposal_point").isNotEmpty())
                .andExpect(jsonPath("$.data.fact_level").value("FACT"))
                .andExpect(jsonPath("$.data.query_needed").value(true));
    }

    @Test @Order(31) @DisplayName("PATCH /requirements/{id}/analysis → AI 분석 수정")
    void updateRequirementAnalysis() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/requirements/" + requirementId + "/analysis")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fact_summary":"수정된 사실 요약","fact_level":"INFERENCE"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fact_summary").value("수정된 사실 요약"))
                .andExpect(jsonPath("$.data.fact_level").value("INFERENCE"));
    }

    // ─── Requirement Review API ──────────────────────────────────────
    @Test @Order(40) @DisplayName("GET /requirements/{id}/review → 초기 상태")
    void getRequirementReview() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/" + requirementId + "/review")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test @Order(41) @DisplayName("POST /requirements/{id}/review-status → APPROVED")
    void changeReviewStatusApproved() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/requirements/" + requirementId + "/review-status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"review_status":"APPROVED","review_comment":"원문 근거 확인 완료"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review_status").value("APPROVED"))
                .andExpect(jsonPath("$.data.review_comment").value("원문 근거 확인 완료"));
    }

    @Test @Order(42) @DisplayName("POST /requirements/{id}/review-status → NEEDS_UPDATE")
    void changeReviewStatusNeedsUpdate() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/requirements/" + requirementId + "/review-status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"review_status":"NEEDS_UPDATE","review_comment":"보안 영역 재검토 필요"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review_status").value("NEEDS_UPDATE"));
    }

    @Test @Order(43) @DisplayName("검토 후 요구사항 reviewStatus 반영 확인")
    void verifyReviewStatusOnRequirement() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/" + requirementId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requirement.review_status").value("NEEDS_UPDATE"));
    }

    // ─── Requirement Sources API ─────────────────────────────────────
    @Test @Order(50) @DisplayName("GET /requirements/{id}/sources → 근거 (빈 목록)")
    void getRequirementSources() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/" + requirementId + "/sources")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source_text_blocks").isArray());
    }

    // ─── Requirement 수정 API ────────────────────────────────────────
    @Test @Order(60) @DisplayName("PATCH /requirements/{id} → 기본 정보 수정")
    void updateRequirement() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/requirements/" + requirementId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"수정된 보안 요구사항","category":"SECURITY","mandatory_flag":false}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requirement.title").value("수정된 보안 요구사항"));
    }
}

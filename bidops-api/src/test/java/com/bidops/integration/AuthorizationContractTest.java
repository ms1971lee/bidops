package com.bidops.integration;

import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.enums.FactLevel;
import com.bidops.domain.requirement.enums.RequirementCategory;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 인증/인가 계약 테스트 (2차 보강).
 *
 * 검증 대상:
 * 1. 토큰 없이 보호 API 접근 → 401 Unauthorized
 * 2. 변조/만료 JWT → 401 Unauthorized
 * 3. 다른 조직 사용자가 프로젝트 하위 리소스 접근 → 403/404
 * 4. 하위 리소스(requirements/{id}, documents, checklists) 포함
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthorizationContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired RequirementRepository requirementRepo;

    static String tokenA;
    static String tokenB;
    static String projectIdA;
    static String documentIdA;
    static String requirementIdA;
    static String checklistIdA;
    static String inquiryIdA;
    private TestHelper h;

    @BeforeEach
    void initHelper() { h = new TestHelper(mvc, om); }

    // ─── Setup ───────────────────────────────���───────────────────────
    @Test @Order(1) @DisplayName("Setup: 사용자 A (조직A) + 프로젝트 + 하위 리소스")
    void setupUserA() throws Exception {
        tokenA = h.signup("AuthUserA", "authA@test.com", "password123", "AuthOrgA");
        projectIdA = h.createProject(tokenA, "A의 보안 테스트 프로젝트");
        documentIdA = h.uploadDocument(tokenA, projectIdA, "auth-rfp.pdf", "RFP");
        checklistIdA = h.createChecklist(tokenA, projectIdA, "A의 체크리스트", "SUBMISSION");
        inquiryIdA = h.createInquiry(tokenA, projectIdA, "A의 질의", "테스트 질문");

        // Requirement를 DB에 직접 삽입
        requirementIdA = TestHelper.insertRequirement(requirementRepo,
                projectIdA, documentIdA, "AUTH-REQ-001", "보안 테스트 요구사항",
                "인증 검증용 원문", RequirementCategory.SECURITY,
                true, true, false, FactLevel.FACT);
    }

    @Test @Order(2) @DisplayName("Setup: 사용자 B (조직B)")
    void setupUserB() throws Exception {
        tokenB = h.signup("AuthUserB", "authB@test.com", "password123", "AuthOrgB");
    }

    // ━━━ 1. 토큰 없이 접근 → 401 Unauthorized ━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(10) @DisplayName("GET /projects/{id} 토큰 없음 → 401 + WWW-Authenticate: Bearer")
    void projectDetailNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test @Order(11) @DisplayName("GET /projects/{id}/documents 토큰 없음 → 401 + JSON 에러 본문")
    void documentsNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test @Order(12) @DisplayName("GET /projects/{id}/requirements/{reqId} 토큰 없음 → 401")
    void requirementDetailNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(13) @DisplayName("GET /projects/{id}/checklists 토큰 없음 → 401")
    void checklistsNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/checklists"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(14) @DisplayName("GET /projects/{id}/inquiries 토큰 없음 → 401")
    void inquiriesNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/inquiries"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(15) @DisplayName("GET /projects/{id}/requirements/{reqId}/analysis 토큰 없음 → 401")
    void requirementAnalysisNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA + "/analysis"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(16) @DisplayName("POST /projects/{id}/requirements/{reqId}/review-status 토큰 없음 → 401")
    void requirementReviewStatusNoToken() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA + "/review-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"review_status":"APPROVED","review_comment":"무단 승인"}
                """))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(17) @DisplayName("PATCH /projects/{id}/requirements/{reqId}/analysis 토큰 없음 → 401")
    void requirementAnalysisPatchNoToken() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA + "/analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fact_summary":"무단 수정"}
                """))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(18) @DisplayName("POST /projects/{id}/inquiries/generate 토큰 없음 → 401")
    void inquiryGenerateNoToken() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectIdA + "/inquiries/generate"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(19) @DisplayName("만료/변조된 JWT → 401 + WWW-Authenticate: Bearer")
    void tamperedToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA)
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.tampered"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    // ━━━ 2. 다른 조직 사용자의 프로젝트 접근 → 403/404 ━━━━━━━━━━━━━━━

    @Test @Order(20) @DisplayName("사용자 B → A의 프로젝트 상세 → 403/404")
    void crossOrgProjectDetail() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA)
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(21) @DisplayName("사용자 B → A의 문서 목록 → 403/404")
    void crossOrgDocuments() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/documents")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(22) @DisplayName("사용자 B → A의 요구사항 상세 → 403/404")
    void crossOrgRequirementDetail() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA)
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(23) @DisplayName("사용자 B → A의 요구사항 분석 조회 → 403/404")
    void crossOrgRequirementAnalysis() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA + "/analysis")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(24) @DisplayName("사용자 B → A의 요구사항 검토 상태 변경 → 403/404")
    void crossOrgReviewStatusChange() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA + "/review-status")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"review_status":"APPROVED","review_comment":"무단 승인 시도"}
                """))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(25) @DisplayName("사용자 B → A의 체크리스트 목록 → 403/404")
    void crossOrgChecklists() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/checklists")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(26) @DisplayName("사용자 B → A의 질의 목록 → 403/404")
    void crossOrgInquiries() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/inquiries")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(27) @DisplayName("사용자 B → A의 질의 자동 생성 → 403/404")
    void crossOrgInquiryGenerate() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectIdA + "/inquiries/generate")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(28) @DisplayName("사용자 B → A의 분석 수정 → 403/404")
    void crossOrgAnalysisPatch() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA + "/analysis")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fact_summary":"무단 수정 시도"}
                """))
                .andExpect(status().is4xxClientError());
    }

    // ━━━ 3. 정상 사용자 A는 자신의 리소스에 접근 가능 (대조군) ━━━━━━━━━

    @Test @Order(30) @DisplayName("사용자 A → 자신의 프로젝트 정상 접근")
    void ownerProjectAccess() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(projectIdA));
    }

    @Test @Order(31) @DisplayName("사용자 A → 자신의 요구사항 정상 접근")
    void ownerRequirementAccess() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/requirements/" + requirementIdA)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test @Order(32) @DisplayName("사용자 A → 자신의 체크리스트 정상 접근")
    void ownerChecklistAccess() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/checklists")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    // ━━━ 4. 공개 엔드포인트 확인 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(40) @DisplayName("POST /auth/signup은 토큰 없이 접근 가능")
    void signupIsPublic() throws Exception {
        int status = mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"공개테스트","email":"authA@test.com","password":"pass123","organization_name":"조직"}
                """))
                .andReturn().getResponse().getStatus();
        // 중복이면 409, 검증 실패면 400 — 401/403은 아님
        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
    }

    @Test @Order(41) @DisplayName("POST /auth/login은 토큰 없이 접근 가능")
    void loginIsPublic() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"authA@test.com","password":"password123"}
                """))
                .andExpect(status().isOk());
    }

}

package com.bidops.integration;

import com.bidops.auth.JwtTokenProvider;
import com.bidops.auth.UserRepository;
import com.bidops.auth.User;
import com.bidops.domain.organization.repository.OrganizationRepository;
import com.bidops.domain.organization.entity.Organization;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BidOps 전체 API 통합 테스트.
 * H2 인메모리 DB + 실제 Spring 컨텍스트로 엔드포인트를 검증한다.
 * 테스트 순서: 회원가입 → 프로젝트 생성 → 문서 업로드 → 요구사항 → 체크리스트 → ...
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtTokenProvider jwtProvider;
    @Autowired UserRepository userRepository;
    @Autowired OrganizationRepository orgRepository;

    // shared state across ordered tests
    static String token;
    static String userId;
    static String orgId;
    static String projectId;
    static String documentId;
    static String checklistId;
    static String checklistItemId;

    private String auth() {
        return "Bearer " + token;
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString()).get("data");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 1. Auth
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(1) @DisplayName("POST /auth/signup → 201")
    void signup() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"테스트관리자","email":"test@bidops.com",
                     "password":"password123","organization_name":"테스트조직"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("test@bidops.com"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        System.out.println("SIGNUP BODY: " + body);
        JsonNode data = dataOf(result);
        token = data.get("token").asText();
        // userId field — try both snake_case variants
        userId = data.has("user_id") ? data.get("user_id").asText() : data.get("userId") != null ? data.get("userId").asText() : null;
        orgId = data.has("organization_id") ? data.get("organization_id").asText() : data.get("organizationId") != null ? data.get("organizationId").asText() : null;

        assertThat(token).isNotBlank();
        assertThat(userId).as("userId should not be null; response: " + body).isNotBlank();
    }

    @Test @Order(2) @DisplayName("POST /auth/signup 이메일 중복 → 4xx/5xx")
    void signupDuplicate() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"중복","email":"test@bidops.com",
                     "password":"pass","organization_name":"조직"}
                """))
                .andReturn();
        int status = result.getResponse().getStatus();
        // Expect 400 (validation), 409 (conflict), or 500
        assertThat(status).as("Duplicate signup should not succeed (got %d)", status)
                .isIn(400, 409, 500);
    }

    @Test @Order(3) @DisplayName("POST /auth/login → 200")
    void login() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"test@bidops.com","password":"password123"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test @Order(4) @DisplayName("POST /auth/login 잘못된 비밀번호 → 4xx")
    void loginWrongPassword() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"test@bidops.com","password":"wrongpassword"}
                """))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertThat(status).as("Wrong password should fail (got %d)", status)
                .isBetween(400, 500);
    }

    @Test @Order(5) @DisplayName("GET /auth/me → 200")
    void getMe() throws Exception {
        mvc.perform(get("/api/v1/auth/me")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@bidops.com"))
                .andExpect(jsonPath("$.data.name").value("테스트관리자"));
    }

    @Test @Order(6) @DisplayName("GET /auth/me 토큰 없음 → 401 or 403")
    void getMeNoToken() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().is4xxClientError());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 2. Projects
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(10) @DisplayName("POST /projects → 생성")
    void createProject() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"테스트 프로젝트","client_name":"테스트발주처",
                     "business_name":"테스트사업","description":"통합테스트용"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("테스트 프로젝트"))
                .andExpect(jsonPath("$.data.client_name").value("테스트발주처"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        projectId = dataOf(result).get("id").asText();
        assertThat(projectId).isNotBlank();
    }

    @Test @Order(11) @DisplayName("POST /projects 필수값 누락 → 400")
    void createProjectMissingName() throws Exception {
        mvc.perform(post("/api/v1/projects")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","client_name":"발주처","business_name":"사업명"}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(12) @DisplayName("GET /projects → 목록 조회")
    void listProjects() throws Exception {
        mvc.perform(get("/api/v1/projects")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].name").value("테스트 프로젝트"));
    }

    @Test @Order(13) @DisplayName("GET /projects/{id} → 상세 조회")
    void getProject() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId)
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(projectId))
                .andExpect(jsonPath("$.data.name").value("테스트 프로젝트"))
                .andExpect(jsonPath("$.data.description").value("통합테스트용"));
    }

    @Test @Order(14) @DisplayName("PATCH /projects/{id} → 수정")
    void updateProject() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId)
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"수정된 프로젝트","description":"수정된 설명"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 프로젝트"));
    }

    @Test @Order(15) @DisplayName("POST /projects/{id}/status → 상태 변경")
    void changeProjectStatus() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"ANALYZING"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANALYZING"));
    }

    @Test @Order(16) @DisplayName("GET /projects 키워드 검색")
    void listProjectsKeyword() throws Exception {
        mvc.perform(get("/api/v1/projects?keyword=수정된")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("수정된 프로젝트"));
    }

    @Test @Order(17) @DisplayName("GET /projects 존재하지 않는 ID → 404")
    void getProjectNotFound() throws Exception {
        mvc.perform(get("/api/v1/projects/nonexistent-id")
                .header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 3. Members
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(20) @DisplayName("GET /members → 목록 (OWNER 포함)")
    void listMembers() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/members")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].project_role").value("OWNER"));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 4. Documents
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(30) @DisplayName("POST /documents → PDF 업로드")
    void uploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-rfp.pdf", "application/pdf",
                "%PDF-1.4 fake content".getBytes());

        MvcResult result = mvc.perform(multipart("/api/v1/projects/" + projectId + "/documents")
                .file(file)
                .param("type", "RFP")
                .header("Authorization", auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.file_name").value("test-rfp.pdf"))
                .andExpect(jsonPath("$.data.type").value("RFP"))
                .andExpect(jsonPath("$.data.parse_status").value("UPLOADED"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn();

        documentId = dataOf(result).get("id").asText();
        assertThat(documentId).isNotBlank();
    }

    @Test @Order(31) @DisplayName("GET /documents → 목록")
    void listDocuments() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/documents")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").value(documentId));
    }

    @Test @Order(32) @DisplayName("GET /documents/{id} → 상세")
    void getDocument() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/documents/" + documentId)
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.file_name").value("test-rfp.pdf"))
                .andExpect(jsonPath("$.data.type").value("RFP"));
    }

    @Test @Order(33) @DisplayName("GET /documents/{id}/versions → 버전 목록")
    void listDocumentVersions() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/documents/" + documentId + "/versions")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test @Order(34) @DisplayName("PATCH /documents/{id}/parse-status → 상태 업데이트")
    void updateParseStatus() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/documents/" + documentId + "/parse-status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"PARSED","page_count":42}
                """))
                .andExpect(status().isOk());

        // Verify updated
        mvc.perform(get("/api/v1/projects/" + projectId + "/documents/" + documentId)
                .header("Authorization", auth()))
                .andExpect(jsonPath("$.data.parse_status").value("PARSED"))
                .andExpect(jsonPath("$.data.page_count").value(42));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 5. Analysis Jobs
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    static String analysisJobId;

    @Test @Order(40) @DisplayName("POST /analysis-jobs → Job 생성")
    void createAnalysisJob() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects/" + projectId + "/analysis-jobs")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"document_id":"%s","job_type":"RFP_PARSE"}
                """.formatted(documentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        analysisJobId = dataOf(result).get("id").asText();
        assertThat(analysisJobId).isNotBlank();
    }

    @Test @Order(41) @DisplayName("GET /analysis-jobs → 목록")
    void listAnalysisJobs() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/analysis-jobs")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].id").value(analysisJobId));
    }

    @Test @Order(42) @DisplayName("GET /analysis-jobs/{id} → 상세")
    void getAnalysisJob() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/analysis-jobs/" + analysisJobId)
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.document_id").value(documentId));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 6. Requirements (목록은 분석 결과가 있어야 의미있으므로 빈 목록 테스트)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(50) @DisplayName("GET /requirements → 빈 목록")
    void listRequirementsEmpty() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test @Order(51) @DisplayName("GET /requirements/quality-stats → 품질 통계")
    void qualityStats() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/quality-stats")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @Order(52) @DisplayName("GET /requirements/reanalyze-status-map → 재분석 상태맵")
    void reanalyzeStatusMap() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/reanalyze-status-map")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @Order(53) @DisplayName("GET /requirements/{id} 존재하지 않는 ID → 404")
    void getRequirementNotFound() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/nonexistent")
                .header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 7. Checklists
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(60) @DisplayName("POST /checklists → 체크리스트 생성")
    void createChecklist() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects/" + projectId + "/checklists")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"checklist_type":"SUBMISSION","title":"제출물 점검표"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("제출물 점검표"))
                .andExpect(jsonPath("$.data.checklist_type").value("SUBMISSION"))
                .andReturn();

        checklistId = dataOf(result).get("id").asText();
        assertThat(checklistId).isNotBlank();
    }

    @Test @Order(61) @DisplayName("GET /checklists → 목록")
    void listChecklists() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("제출물 점검표"));
    }

    @Test @Order(62) @DisplayName("POST /checklists/{id}/items → 항목 생성")
    void createChecklistItem() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"item_text":"사업수행계획서 제출","mandatory_flag":true,
                     "risk_level":"HIGH","risk_note":"마감 3일 전 준비 필요"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.item_text").value("사업수행계획서 제출"))
                .andExpect(jsonPath("$.data.mandatory_flag").value(true))
                .andExpect(jsonPath("$.data.current_status").value("TODO"))
                .andExpect(jsonPath("$.data.risk_level").value("HIGH"))
                .andReturn();

        checklistItemId = dataOf(result).get("id").asText();
        assertThat(checklistItemId).isNotBlank();
    }

    @Test @Order(63) @DisplayName("GET /checklists/{id}/items → 항목 목록")
    void listChecklistItems() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].item_code").isNotEmpty());
    }

    @Test @Order(64) @DisplayName("PATCH /checklists/{cid}/items/{iid} → 항목 수정")
    void updateChecklistItem() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + checklistItemId)
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"risk_level":"MEDIUM","action_comment":"초안 작성 시작"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.risk_level").value("MEDIUM"))
                .andExpect(jsonPath("$.data.action_comment").value("초안 작성 시작"));
    }

    @Test @Order(65) @DisplayName("POST /checklists/{cid}/items/{iid}/status → 상태 변경")
    void changeChecklistItemStatus() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + checklistItemId + "/status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_status").value("IN_PROGRESS"));
    }

    @Test @Order(66) @DisplayName("POST /checklists/{cid}/items/{iid}/status → DONE")
    void checklistItemDone() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + checklistItemId + "/status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"DONE"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_status").value("DONE"));
    }

    @Test @Order(67) @DisplayName("GET /checklists/{cid}/items/{iid}/reviews → 이력")
    void listChecklistItemReviews() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + checklistItemId + "/reviews")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(68) @DisplayName("GET /checklists/{id}/items 필터링 (status)")
    void listChecklistItemsFiltered() throws Exception {
        // DONE status
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items?status=DONE")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].current_status").value("DONE"));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 8. Inquiries
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    static String inquiryId;

    @Test @Order(70) @DisplayName("POST /inquiries → 질의 생성")
    void createInquiry() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"ISMS-P 인증 필수 여부","question_text":"동등 수준 체계로 대체 가능한지 확인",
                     "priority":"HIGH"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        inquiryId = dataOf(result).get("id").asText();
        assertThat(inquiryId).isNotBlank();
    }

    @Test @Order(71) @DisplayName("GET /inquiries → 목록")
    void listInquiries() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(72) @DisplayName("GET /inquiries/{id} → 상세")
    void getInquiry() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/inquiries/" + inquiryId)
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(inquiryId));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 9. Artifacts
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    static String artifactId;

    @Test @Order(80) @DisplayName("POST /artifacts → 산출물 생성")
    void createArtifact() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/projects/" + projectId + "/artifacts")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"요구사항 정의서","asset_type":"PROPOSAL","description":"RFP 기반 요구사항 정의"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("요구사항 정의서"))
                .andReturn();

        artifactId = dataOf(result).get("id").asText();
        assertThat(artifactId).isNotBlank();
    }

    @Test @Order(81) @DisplayName("GET /artifacts → 목록")
    void listArtifacts() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/artifacts")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(82) @DisplayName("PATCH /artifacts/{id} → 수정")
    void updateArtifact() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/artifacts/" + artifactId)
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"수정된 요구사항 정의서","description":"v2 업데이트"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 요구사항 정의서"));
    }

    @Test @Order(83) @DisplayName("POST /artifacts/{id}/status → 상태 변경")
    void changeArtifactStatus() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/artifacts/" + artifactId + "/status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test @Order(84) @DisplayName("GET /artifacts/{id}/versions → 빈 버전 목록")
    void listArtifactVersions() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/artifacts/" + artifactId + "/versions")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 10. Audit Logs / Activities
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(90) @DisplayName("GET /audit-logs → 활동 이력")
    void listAuditLogs() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/audit-logs")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 11. Cleanup — 삭제 API 테스트
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(95) @DisplayName("DELETE /artifacts/{id} → 삭제")
    void deleteArtifact() throws Exception {
        mvc.perform(delete("/api/v1/projects/" + projectId + "/artifacts/" + artifactId)
                .header("Authorization", auth()))
                .andExpect(status().isOk());
    }

    @Test @Order(96) @DisplayName("DELETE /documents/{id} → 삭제")
    void deleteDocument() throws Exception {
        mvc.perform(delete("/api/v1/projects/" + projectId + "/documents/" + documentId)
                .header("Authorization", auth()))
                .andExpect(status().isOk());

        // 삭제 후 목록에서 제거 확인
        mvc.perform(get("/api/v1/projects/" + projectId + "/documents")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
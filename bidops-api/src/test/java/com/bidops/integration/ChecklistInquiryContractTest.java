package com.bidops.integration;

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
 * 체크리스트 + 질의 계약 테스트 보강.
 * enum 값, 상태 전이, CRUD 전체 흐름을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChecklistInquiryContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    static String token;
    static String projectId;
    static String checklistId;
    static String itemId;
    static String inquiryId;
    private TestHelper h;

    @BeforeEach
    void initHelper() { h = new TestHelper(mvc, om); }

    private String auth() { return "Bearer " + token; }

    // ─── Setup ───────────────────────────────────────────────────────
    @Test @Order(1) @DisplayName("Setup: 사용자 + 프로젝트")
    void setup() throws Exception {
        token = h.signup("체크리스트테스트", "cltest@test.com", "password123", "CL조직");
        projectId = h.createProject(token, "체크리스트 테스트");
    }

    // ━━━ Checklist enum/상태 전이 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Test @Order(10) @DisplayName("SUBMISSION 유형 체크리스트 생성")
    void createSubmissionChecklist() throws Exception {
        checklistId = h.createChecklist(token, projectId, "제출물 점검", "SUBMISSION");
        assertThat(checklistId).isNotBlank();
    }

    @Test @Order(11) @DisplayName("EVALUATION 유형 체크리스트 생성")
    void createEvaluationChecklist() throws Exception {
        String id = h.createChecklist(token, projectId, "평가 점검", "EVALUATION");
        assertThat(id).isNotBlank();
    }

    @Test @Order(12) @DisplayName("EVIDENCE 유형 체크리스트 생성")
    void createEvidenceChecklist() throws Exception {
        String id = h.createChecklist(token, projectId, "증빙 점검", "EVIDENCE");
        assertThat(id).isNotBlank();
    }

    // ─── Item status transitions ─────────────────────────────────────
    @Test @Order(20) @DisplayName("항목 생성: TODO 기본값 + HIGH 위험도")
    void createHighRiskItem() throws Exception {
        itemId = h.createChecklistItem(token, projectId, checklistId, "보안 아키텍처 설계서", true, "HIGH");

        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + itemId)
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_status").value("TODO"))
                .andExpect(jsonPath("$.data.risk_level").value("HIGH"))
                .andExpect(jsonPath("$.data.mandatory_flag").value(true));
    }

    @Test @Order(21) @DisplayName("상태 전이: TODO → IN_PROGRESS")
    void statusTodoToInProgress() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + itemId + "/status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_status").value("IN_PROGRESS"));
    }

    @Test @Order(22) @DisplayName("상태 전이: IN_PROGRESS → BLOCKED")
    void statusToBlocked() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + itemId + "/status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"BLOCKED"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_status").value("BLOCKED"));
    }

    @Test @Order(23) @DisplayName("상태 전이: BLOCKED → DONE")
    void statusBlockedToDone() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + itemId + "/status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"DONE"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current_status").value("DONE"));
    }

    // ─── Risk level enum validation ──────────────────────────────────
    @Test @Order(30) @DisplayName("NONE 위험도 항목 생성")
    void createNoneRiskItem() throws Exception {
        String id = h.createChecklistItem(token, projectId, checklistId, "일반 항목", false, "NONE");
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + id)
                .header("Authorization", auth()))
                .andExpect(jsonPath("$.data.risk_level").value("NONE"));
    }

    @Test @Order(31) @DisplayName("MEDIUM 위험도 항목 생성")
    void createMediumRiskItem() throws Exception {
        String id = h.createChecklistItem(token, projectId, checklistId, "중간 위험 항목", true, "MEDIUM");
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + id)
                .header("Authorization", auth()))
                .andExpect(jsonPath("$.data.risk_level").value("MEDIUM"));
    }

    // ─── 항목 수정: action_comment, owner ─────────────────────────────
    @Test @Order(35) @DisplayName("항목 수정: action_comment 추가")
    void updateItemComment() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + itemId)
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action_comment":"보안팀 검토 완료","risk_note":"ISMS-P 기준 충족 확인"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action_comment").value("보안팀 검토 완료"))
                .andExpect(jsonPath("$.data.risk_note").value("ISMS-P 기준 충족 확인"));
    }

    // ─── Review 이력 누적 확인 ───────────────────────────────────────
    @Test @Order(36) @DisplayName("상태 변경 이력이 누적되는지 확인")
    void checkReviewHistory() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items/" + itemId + "/reviews")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                // 3회 상태 변경 → 최소 3건
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    // ─── 필터 조합 테스트 ────────────────────────────────────────────
    @Test @Order(37) @DisplayName("필터: risk_level=HIGH")
    void filterByRisk() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items?risk_level=HIGH")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].risk_level").value("HIGH"));
    }

    @Test @Order(38) @DisplayName("필터: mandatory=true")
    void filterByMandatory() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items?mandatory=true")
                .header("Authorization", auth()))
                .andExpect(status().isOk());
    }

    // ━━━ Inquiry 계약 테스트 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Test @Order(50) @DisplayName("질의 생성: 필수 필드 검증")
    void createInquiry() throws Exception {
        inquiryId = h.createInquiry(token, projectId, "ISMS-P 질의", "인증 필수 여부 확인");
        assertThat(inquiryId).isNotBlank();
    }

    @Test @Order(51) @DisplayName("질의 목록 조회")
    void listInquiries() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("ISMS-P 질의"));
    }

    @Test @Order(52) @DisplayName("질의 수정: PATCH")
    void updateInquiry() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/inquiries/" + inquiryId)
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"수정된 ISMS-P 질의","question_text":"수정된 질문"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 ISMS-P 질의"));
    }

    @Test @Order(53) @DisplayName("질의 상태 변경: DRAFT → SUBMITTED")
    void changeInquiryStatus() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries/" + inquiryId + "/status")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"SUBMITTED"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test @Order(54) @DisplayName("질의 필수값 누락 → 400")
    void createInquiryMissingFields() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":""}
                """))
                .andExpect(status().isBadRequest());
    }
}

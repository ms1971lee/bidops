package com.bidops.integration;

import com.bidops.domain.requirement.enums.FactLevel;
import com.bidops.domain.requirement.enums.RequirementCategory;
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
 * 질의(Inquiry) 자동 생성 + 상태 전이 + enum 검증 계약 테스트.
 *
 * 검증 대상:
 * 1. POST /inquiries/generate → queryNeeded=true인 요구사항 기반 자동 생성
 * 2. 중복 호출 시 skip 동작
 * 3. 질의 상태 전이: DRAFT → SUBMITTED → ANSWERED → CLOSED
 * 4. 질의 우선순위 enum: LOW, MEDIUM, HIGH, CRITICAL
 * 5. PATCH /inquiries/{id} → 필드 수정
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InquiryGenerateContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired RequirementRepository requirementRepo;
    @Autowired RequirementInsightRepository insightRepo;

    static String token;
    static String projectId;
    static String documentId;
    static String reqId1; // queryNeeded=true
    static String reqId2; // queryNeeded=true
    static String reqId3; // queryNeeded=false (대조군)
    static String generatedInquiryId;
    private TestHelper h;

    @BeforeEach
    void initHelper() { h = new TestHelper(mvc, om); }

    private JsonNode dataOf(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString()).get("data");
    }

    // ─── Setup ───────────────────────────────────────────────────────
    @Test @Order(1) @DisplayName("Setup: 환경 구성 + 요구사항 3건 삽입")
    void setup() throws Exception {
        TestHelper.TestEnvironment env = h.setupFullEnvironment("inqgen");
        token = env.token();
        projectId = env.projectId();
        documentId = env.documentId();

        // queryNeeded=true 2건
        reqId1 = TestHelper.insertRequirement(requirementRepo,
                projectId, documentId, "GEN-REQ-001", "보안 인증 요구사항",
                "ISMS-P 인증 필수 여부 불명확",
                RequirementCategory.SECURITY, true, true, true, FactLevel.REVIEW_NEEDED);

        reqId2 = TestHelper.insertRequirement(requirementRepo,
                projectId, documentId, "GEN-REQ-002", "성능 기준 모호성",
                "동시접속 사용자 수 기준이 명확하지 않음",
                RequirementCategory.PERFORMANCE, true, false, true, FactLevel.INFERENCE);

        // queryNeeded=false 1건 (대조군)
        reqId3 = TestHelper.insertRequirement(requirementRepo,
                projectId, documentId, "GEN-REQ-003", "명확한 요구사항",
                "서버 가용성 99.9% 이상 유지",
                RequirementCategory.INFRASTRUCTURE, true, true, false, FactLevel.FACT);
    }

    // ━━━ 1. 질의 자동 생성 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(10) @DisplayName("POST /inquiries/generate → queryNeeded=true 요구사항 기반 생성")
    void generateInquiries() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries/generate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.created_count").value(2))
                .andExpect(jsonPath("$.data.skipped_count").value(0))
                .andExpect(jsonPath("$.data.created_inquiry_ids").isArray())
                .andReturn();

        JsonNode data = dataOf(r);
        assertThat(data.get("created_inquiry_ids").size()).isEqualTo(2);
        generatedInquiryId = data.get("created_inquiry_ids").get(0).asText();
    }

    @Test @Order(11) @DisplayName("생성된 질의 상세 확인: 코드 형식 + DRAFT 상태 + MEDIUM 우선순위")
    void verifyGeneratedInquiry() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/projects/" + projectId + "/inquiries/" + generatedInquiryId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.data.inquiry_code").isNotEmpty())
                .andExpect(jsonPath("$.data.question_text").isNotEmpty())
                .andReturn();

        // INQ-XXX 코드 형식 검증
        String code = dataOf(r).get("inquiry_code").asText();
        assertThat(code).matches("INQ-\\d{3}");
    }

    @Test @Order(12) @DisplayName("POST /inquiries/generate 중복 호출 → skip 처리")
    void generateInquiriesDuplicate() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries/generate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created_count").value(0))
                .andExpect(jsonPath("$.data.skipped_count").value(2));
    }

    // ━━━ 2. 질의 상태 전이 전체 사이클 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(20) @DisplayName("상태 전이: DRAFT → SUBMITTED")
    void statusDraftToSubmitted() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries/" + generatedInquiryId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"SUBMITTED"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test @Order(21) @DisplayName("상태 전이: SUBMITTED → ANSWERED")
    void statusSubmittedToAnswered() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries/" + generatedInquiryId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"ANSWERED"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"));
    }

    @Test @Order(22) @DisplayName("상태 전이: ANSWERED → CLOSED")
    void statusAnsweredToClosed() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries/" + generatedInquiryId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"CLOSED"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    // ━━━ 3. 우선순위 enum 검증 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(30) @DisplayName("질의 생성: LOW 우선순위")
    void createLowPriority() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"LOW 우선순위 질의","question_text":"낮은 우선순위 확인","priority":"LOW"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.priority").value("LOW"));
    }

    @Test @Order(31) @DisplayName("질의 생성: HIGH 우선순위")
    void createHighPriority() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"HIGH 우선순위 질의","question_text":"높은 우선순위 확인","priority":"HIGH"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.priority").value("HIGH"));
    }

    @Test @Order(32) @DisplayName("질의 생성: CRITICAL 우선순위")
    void createCriticalPriority() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"CRITICAL 우선순위 질의","question_text":"긴급 확인","priority":"CRITICAL"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.priority").value("CRITICAL"));
    }

    // ━━━ 4. PATCH 질의 수정 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(40) @DisplayName("PATCH /inquiries/{id} → 제목/질문/답변 수정")
    void updateInquiryFields() throws Exception {
        // 먼저 새 질의를 생성
        MvcResult r = mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"수정 대상 질의","question_text":"원래 질문","priority":"MEDIUM"}
                """))
                .andExpect(status().isCreated())
                .andReturn();
        String patchTargetId = dataOf(r).get("id").asText();

        // PATCH로 수정
        mvc.perform(patch("/api/v1/projects/" + projectId + "/inquiries/" + patchTargetId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"수정된 질의 제목","question_text":"수정된 질문 내용"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정된 질의 제목"))
                .andExpect(jsonPath("$.data.question_text").value("수정된 질문 내용"));
    }

    // ━━━ 5. 필터 조합 테스트 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(50) @DisplayName("질의 필터: status=DRAFT")
    void filterByStatus() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/inquiries?status=DRAFT")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @Order(51) @DisplayName("질의 필터: priority=HIGH")
    void filterByPriority() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/inquiries?priority=HIGH")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].priority").value("HIGH"));
    }

    // ━━━ 6. 유효성 검증 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(60) @DisplayName("질의 생성: 필수 필드 누락 → 400")
    void createInquiryMissingTitle() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"","question_text":"질문"}
                """))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(61) @DisplayName("질의 생성: question_text 누락 → 400")
    void createInquiryMissingQuestion() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"제목만"}
                """))
                .andExpect(status().isBadRequest());
    }
}

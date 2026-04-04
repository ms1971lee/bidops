package com.bidops.integration;

import com.bidops.domain.requirement.enums.FactLevel;
import com.bidops.domain.requirement.enums.RequirementCategory;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /projects/{projectId}/checklists/generate 계약 테스트.
 *
 * 검증 대상:
 * 1. 최초 생성 성공 (요구사항 기반)
 * 2. 중복 호출 시 skip
 * 3. 생성 건수 + 위험도 반영 검증
 * 4. 권한 없는 사용자 차단
 * 5. 요구사항 없는 프로젝트 → 빈 결과
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChecklistGenerateContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired RequirementRepository requirementRepo;

    static String tokenA;
    static String tokenB;
    static String projectId;
    static String documentId;
    static String checklistId;
    private TestHelper h;

    @BeforeEach
    void initHelper() { h = new TestHelper(mvc, om); }

    private JsonNode dataOf(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString()).get("data");
    }

    // ─── Setup ───────────────────────────────────────────────────────
    @Test @Order(1) @DisplayName("Setup: 사용자 + 프로젝트 + 문서 + 요구사항 3건")
    void setup() throws Exception {
        TestHelper.TestEnvironment env = h.setupFullEnvironment("clgen");
        tokenA = env.token();
        projectId = env.projectId();
        documentId = env.documentId();

        // 1: mandatory + evidenceRequired → HIGH
        TestHelper.insertRequirement(requirementRepo,
                projectId, documentId, "CG-REQ-001", "보안 인증 필수",
                "ISMS-P 인증 수준의 보안 체계를 갖추어야 한다.",
                RequirementCategory.SECURITY, true, true, false, FactLevel.FACT);

        // 2: mandatory only → MEDIUM
        TestHelper.insertRequirement(requirementRepo,
                projectId, documentId, "CG-REQ-002", "성능 기준",
                "동시접속 1000명 이상 지원",
                RequirementCategory.PERFORMANCE, true, false, false, FactLevel.FACT);

        // 3: queryNeeded → MEDIUM
        TestHelper.insertRequirement(requirementRepo,
                projectId, documentId, "CG-REQ-003", "모호한 데이터 연계",
                "관련 시스템과 연계",
                RequirementCategory.DATA_INTEGRATION, false, false, true, FactLevel.INFERENCE);
    }

    @Test @Order(2) @DisplayName("Setup: 다른 조직 사용자 B 생성")
    void setupUserB() throws Exception {
        tokenB = h.signup("CLGenUserB", "clgenB@test.com", "password123", "CLGenOrgB");
    }

    // ━━━ 1. 최초 생성 성공 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(10) @DisplayName("POST /checklists/generate → 3건 생성")
    void generateFirstTime() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/generate")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.created_count").value(3))
                .andExpect(jsonPath("$.data.skipped_count").value(0))
                .andExpect(jsonPath("$.data.created_item_ids").isArray())
                .andExpect(jsonPath("$.data.checklist_id").isNotEmpty())
                .andReturn();

        JsonNode data = dataOf(r);
        checklistId = data.get("checklist_id").asText();
        assertThat(data.get("created_item_ids").size()).isEqualTo(3);
    }

    // ━━━ 2. 생성된 항목 검증 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(11) @DisplayName("생성된 체크리스트 확인: SUBMISSION 유형")
    void verifyChecklist() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId)
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checklist_type").value("SUBMISSION"))
                .andExpect(jsonPath("$.data.total_count").value(3));
    }

    @Test @Order(12) @DisplayName("항목 위험도 검증: HIGH/MEDIUM/LOW 반영")
    void verifyItemRiskLevels() throws Exception {
        // HIGH 위험도 항목 존재
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items?risk_level=HIGH")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].risk_level").value("HIGH"))
                .andExpect(jsonPath("$.data[0].mandatory_flag").value(true));

        // MEDIUM 위험도 항목 존재
        mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items?risk_level=MEDIUM")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test @Order(13) @DisplayName("항목 코드 형식: CHK-XXX")
    void verifyItemCodes() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = om.readTree(r.getResponse().getContentAsString()).get("data");
        for (JsonNode item : items) {
            String code = item.get("item_code").asText();
            assertThat(code).matches("CHK-\\d{3}");
            // linkedRequirementId가 설정되어 있는지 확인
            assertThat(item.has("linked_requirement_id")).isTrue();
        }
    }

    // ━━━ 3. 중복 호출 → skip ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(20) @DisplayName("POST /checklists/generate 중복 호출 → skip")
    void generateDuplicate() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/generate")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created_count").value(0))
                .andExpect(jsonPath("$.data.skipped_count").value(3))
                .andExpect(jsonPath("$.data.checklist_id").value(checklistId));
    }

    // ━━━ 4. 권한 없는 사용자 → 차단 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(30) @DisplayName("사용자 B → 다른 조직 프로젝트 generate → 403/404")
    void generateUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/generate")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(31) @DisplayName("토큰 없이 generate → 401")
    void generateNoToken() throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/generate"))
                .andExpect(status().isUnauthorized());
    }

    // ━━━ 5. 빈 프로젝트에서 generate ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(40) @DisplayName("요구사항 없는 프로젝트 → 빈 결과")
    void generateEmptyProject() throws Exception {
        String emptyProjectId = h.createProject(tokenA, "빈 프로젝트");

        mvc.perform(post("/api/v1/projects/" + emptyProjectId + "/checklists/generate")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created_count").value(0))
                .andExpect(jsonPath("$.data.skipped_count").value(0));
    }
}

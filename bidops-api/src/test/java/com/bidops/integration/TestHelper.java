package com.bidops.integration;

import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.enums.*;
import com.bidops.domain.requirement.repository.RequirementInsightRepository;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 통합 테스트용 데이터 생성 헬퍼.
 * 각 메서드가 API를 호출해 데이터를 생성하고 ID를 반환한다.
 */
class TestHelper {

    private final MockMvc mvc;
    private final ObjectMapper om;

    TestHelper(MockMvc mvc, ObjectMapper om) {
        this.mvc = mvc;
        this.om = om;
    }

    JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode root = om.readTree(body);
        return root.get("data");
    }

    /** 회원가입 후 토큰 반환. */
    String signup(String name, String email, String password, String orgName) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","email":"%s","password":"%s","organization_name":"%s"}
                """.formatted(name, email, password, orgName)))
                .andReturn();
        assertThat(r.getResponse().getStatus()).as("signup should succeed").isIn(200, 201);
        return dataOf(r).get("token").asText();
    }

    /** 프로젝트 생성 후 ID 반환. */
    String createProject(String token, String name) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","client_name":"테스트발주","business_name":"테스트사업"}
                """.formatted(name)))
                .andReturn();
        assertThat(r.getResponse().getStatus()).as("createProject should succeed").isIn(200, 201);
        return dataOf(r).get("id").asText();
    }

    /** 문서 업로드 후 ID 반환. */
    String uploadDocument(String token, String projectId, String fileName, String type) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "application/pdf", "%PDF-1.4 fake".getBytes());
        MvcResult r = mvc.perform(multipart("/api/v1/projects/" + projectId + "/documents")
                .file(file).param("type", type)
                .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(r.getResponse().getStatus()).as("upload should succeed").isIn(200, 201);
        return dataOf(r).get("id").asText();
    }

    /** 체크리스트 생성 후 ID 반환. */
    String createChecklist(String token, String projectId, String title, String type) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/projects/" + projectId + "/checklists")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"checklist_type":"%s","title":"%s"}
                """.formatted(type, title)))
                .andReturn();
        assertThat(r.getResponse().getStatus()).as("createChecklist should succeed").isIn(200, 201);
        return dataOf(r).get("id").asText();
    }

    /** 체크리스트 항목 생성 후 ID 반환. */
    String createChecklistItem(String token, String projectId, String checklistId, String text, boolean mandatory, String riskLevel) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/projects/" + projectId + "/checklists/" + checklistId + "/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"item_text":"%s","mandatory_flag":%s,"risk_level":"%s"}
                """.formatted(text, mandatory, riskLevel)))
                .andReturn();
        assertThat(r.getResponse().getStatus()).as("createChecklistItem should succeed").isIn(200, 201);
        return dataOf(r).get("id").asText();
    }

    /** 질의 생성 후 ID 반환. */
    String createInquiry(String token, String projectId, String title, String questionText) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/projects/" + projectId + "/inquiries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"%s","question_text":"%s","priority":"MEDIUM"}
                """.formatted(title, questionText)))
                .andReturn();
        assertThat(r.getResponse().getStatus()).as("createInquiry should succeed").isIn(200, 201);
        return dataOf(r).get("id").asText();
    }

    // ─── Fixture Builder: DB 직접 삽입 ─────────────────────────────

    /**
     * Requirement를 DB에 직접 삽입하고 ID를 반환한다.
     * API를 거치지 않고 테스트 데이터를 준비할 때 사용.
     */
    static String insertRequirement(RequirementRepository repo,
                                    String projectId, String documentId,
                                    String code, String title, String originalText,
                                    RequirementCategory category,
                                    boolean mandatory, boolean evidenceRequired,
                                    boolean queryNeeded, FactLevel factLevel) {
        Requirement req = Requirement.builder()
                .projectId(projectId)
                .documentId(documentId)
                .requirementCode(code)
                .title(title)
                .originalText(originalText)
                .category(category)
                .mandatoryFlag(mandatory)
                .evidenceRequiredFlag(evidenceRequired)
                .queryNeeded(queryNeeded)
                .factLevel(factLevel)
                .confidenceScore(0.85f)
                .build();
        return repo.save(req).getId();
    }

    /**
     * RequirementInsight를 DB에 직접 삽입한다.
     */
    static void insertInsight(RequirementInsightRepository repo,
                              String requirementId,
                              String factSummary,
                              String interpretationSummary,
                              boolean queryNeeded,
                              FactLevel factLevel) {
        RequirementInsight insight = RequirementInsight.builder()
                .requirementId(requirementId)
                .factSummary(factSummary)
                .interpretationSummary(interpretationSummary)
                .intentSummary("테스트 의도 요약")
                .proposalPoint("테스트 제안 포인트")
                .implementationApproach("테스트 구현 방향")
                .queryNeeded(queryNeeded)
                .factLevel(factLevel)
                .build();
        repo.save(insight);
    }

    /**
     * 테스트용 전체 환경 셋업: 사용자 가입 + 프로젝트 생성 + 문서 업로드.
     * 반환: {token, projectId, documentId}
     */
    TestEnvironment setupFullEnvironment(String userPrefix) throws Exception {
        String token = signup(userPrefix + "User", userPrefix + "@test.com", "password123", userPrefix + "Org");
        String projectId = createProject(token, userPrefix + " 프로젝트");
        String documentId = uploadDocument(token, projectId, userPrefix + "-rfp.pdf", "RFP");
        return new TestEnvironment(token, projectId, documentId);
    }

    record TestEnvironment(String token, String projectId, String documentId) {}
}

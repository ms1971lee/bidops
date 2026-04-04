package com.bidops.integration;

import com.bidops.domain.checklist.entity.ChecklistItem;
import com.bidops.domain.checklist.entity.SubmissionChecklist;
import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.bidops.domain.checklist.enums.ChecklistType;
import com.bidops.domain.checklist.enums.RiskLevel;
import com.bidops.domain.checklist.repository.ChecklistItemRepository;
import com.bidops.domain.checklist.repository.SubmissionChecklistRepository;
import com.bidops.domain.inquiry.entity.Inquiry;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.enums.InquiryStatus;
import com.bidops.domain.inquiry.repository.InquiryRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PostgreSQL Testcontainers 기반 운영DB 유사성 검증 테스트.
 *
 * 검증 대상:
 * 1. DDL 생성 (Hibernate create-drop이 PostgreSQL에서 정상 동작)
 * 2. enum → VARCHAR 매핑 정합성
 * 3. TEXT 컬럼 (긴 문자열, JSON 저장) 정합성
 * 4. 제약조건 (NOT NULL, UNIQUE) 동작
 * 5. 전체 API 흐름이 PostgreSQL에서도 동작
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIf(value = "isDockerAvailable", disabledReason = "Docker가 실행 중이지 않아 Testcontainers를 사용할 수 없습니다")
class PostgresContractTest {

    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("bidops_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired RequirementRepository requirementRepo;
    @Autowired RequirementInsightRepository insightRepo;
    @Autowired SubmissionChecklistRepository checklistRepo;
    @Autowired ChecklistItemRepository checklistItemRepo;
    @Autowired InquiryRepository inquiryRepo;

    static String token;
    static String projectId;
    static String documentId;
    private TestHelper h;

    @BeforeEach
    void initHelper() { h = new TestHelper(mvc, om); }

    private JsonNode dataOf(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString()).get("data");
    }

    // ━━━ 1. DDL + 기본 API 흐름 검증 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(1) @DisplayName("[PG] DDL 생성 성공: 회원가입 + 프로젝트 생성 + 문서 업로드")
    void ddlAndBasicFlow() throws Exception {
        // Hibernate가 create-drop으로 PostgreSQL에 DDL을 생성하고 API가 동작하면 성공
        token = h.signup("PG테스트", "pgtest@test.com", "password123", "PG조직");
        assertThat(token).isNotBlank();

        projectId = h.createProject(token, "PostgreSQL 테스트 프로젝트");
        assertThat(projectId).isNotBlank();

        documentId = h.uploadDocument(token, projectId, "pg-rfp.pdf", "RFP");
        assertThat(documentId).isNotBlank();
    }

    // ━━━ 2. Enum 컬럼 검증 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(10) @DisplayName("[PG] Requirement enum 저장/조회: category, factLevel, reviewStatus")
    void requirementEnumFields() {
        Requirement req = Requirement.builder()
                .projectId(projectId)
                .documentId(documentId)
                .requirementCode("PG-REQ-001")
                .title("PostgreSQL 호환 테스트")
                .originalText("enum 값이 PostgreSQL VARCHAR에 올바르게 저장되는지 확인")
                .category(RequirementCategory.NON_FUNCTIONAL)
                .mandatoryFlag(true)
                .evidenceRequiredFlag(false)
                .queryNeeded(false)
                .factLevel(FactLevel.INFERENCE)
                .build();
        req = requirementRepo.save(req);

        Requirement loaded = requirementRepo.findById(req.getId()).orElseThrow();
        assertThat(loaded.getCategory()).isEqualTo(RequirementCategory.NON_FUNCTIONAL);
        assertThat(loaded.getFactLevel()).isEqualTo(FactLevel.INFERENCE);
        assertThat(loaded.getReviewStatus()).isEqualTo(RequirementReviewStatus.NOT_REVIEWED);
        assertThat(loaded.getAnalysisStatus()).isEqualTo(RequirementAnalysisStatus.EXTRACTED);
    }

    @Test @Order(11) @DisplayName("[PG] Checklist enum 저장/조회: checklistType, itemStatus, riskLevel")
    void checklistEnumFields() {
        SubmissionChecklist cl = SubmissionChecklist.builder()
                .projectId(projectId)
                .title("PG 체크리스트")
                .checklistType(ChecklistType.EVIDENCE)
                .build();
        cl = checklistRepo.save(cl);

        ChecklistItem item = ChecklistItem.builder()
                .checklistId(cl.getId())
                .itemCode("PG-CL-001")
                .itemText("증빙 자료 준비")
                .mandatoryFlag(true)
                .currentStatus(ChecklistItemStatus.IN_PROGRESS)
                .riskLevel(RiskLevel.HIGH)
                .build();
        item = checklistItemRepo.save(item);

        ChecklistItem loaded = checklistItemRepo.findById(item.getId()).orElseThrow();
        assertThat(loaded.getCurrentStatus()).isEqualTo(ChecklistItemStatus.IN_PROGRESS);
        assertThat(loaded.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test @Order(12) @DisplayName("[PG] Inquiry enum 저장/조회: status, priority")
    void inquiryEnumFields() {
        Inquiry inq = Inquiry.builder()
                .projectId(projectId)
                .inquiryCode("PG-INQ-001")
                .title("PostgreSQL 질의 테스트")
                .questionText("JSONB와 TEXT 차이 확인 필요")
                .status(InquiryStatus.SUBMITTED)
                .priority(InquiryPriority.CRITICAL)
                .build();
        inq = inquiryRepo.save(inq);

        Inquiry loaded = inquiryRepo.findById(inq.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(InquiryStatus.SUBMITTED);
        assertThat(loaded.getPriority()).isEqualTo(InquiryPriority.CRITICAL);
    }

    // ━━━ 3. TEXT + JSON 컬럼 검증 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(20) @DisplayName("[PG] RequirementInsight: TEXT/JSON 필드 저장 + 조회")
    void insightTextAndJsonFields() {
        // Requirement 먼저 생성
        String reqId = TestHelper.insertRequirement(requirementRepo,
                projectId, documentId, "PG-REQ-TEXT", "TEXT 컬럼 테스트",
                "긴 원문 텍스트: " + "가".repeat(2000),
                RequirementCategory.SECURITY, true, true, true, FactLevel.FACT);

        RequirementInsight insight = RequirementInsight.builder()
                .requirementId(reqId)
                .factSummary("사실 요약 (PostgreSQL TEXT)")
                .interpretationSummary("해석 요약: " + "나".repeat(1000))
                .intentSummary("의도 요약")
                .proposalPoint("제안 포인트")
                .implementationApproach("구현 방향")
                .expectedDeliverablesJson("[\"산출물A\",\"산출물B\",\"산출물C\"]")
                .riskNoteJson("[\"리스크1: PostgreSQL 호환성\",\"리스크2: 성능 차이\"]")
                .qualityIssuesJson("[{\"code\":\"QI001\",\"severity\":\"WARN\",\"message\":\"테스트\"}]")
                .queryNeeded(true)
                .factLevel(FactLevel.FACT)
                .build();
        insightRepo.save(insight);

        RequirementInsight loaded = insightRepo.findByRequirementId(reqId).orElseThrow();
        assertThat(loaded.getFactSummary()).isEqualTo("사실 요약 (PostgreSQL TEXT)");
        assertThat(loaded.getInterpretationSummary()).hasSize(1000 + "해석 요약: ".length());
        assertThat(loaded.getExpectedDeliverablesJson()).contains("산출물A");
        assertThat(loaded.getRiskNoteJson()).contains("PostgreSQL 호환성");
        assertThat(loaded.getQualityIssuesJson()).contains("QI001");
    }

    @Test @Order(21) @DisplayName("[PG] Requirement: 긴 originalText (TEXT 컬럼) 저장")
    void longOriginalText() {
        String longText = "요구사항 원문 ".repeat(500); // ~3500자
        Requirement req = Requirement.builder()
                .projectId(projectId)
                .documentId(documentId)
                .requirementCode("PG-REQ-LONG")
                .title("긴 텍스트 테스트")
                .originalText(longText)
                .category(RequirementCategory.MAINTENANCE)
                .mandatoryFlag(false)
                .evidenceRequiredFlag(false)
                .queryNeeded(false)
                .factLevel(FactLevel.FACT)
                .build();
        req = requirementRepo.save(req);

        Requirement loaded = requirementRepo.findById(req.getId()).orElseThrow();
        assertThat(loaded.getOriginalText()).isEqualTo(longText);
    }

    // ━━━ 4. 제약조건 검증 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test @Order(30) @DisplayName("[PG] RequirementInsight UNIQUE 제약: 동일 requirementId 중복 → 예외")
    void uniqueConstraintInsight() {
        String reqId = TestHelper.insertRequirement(requirementRepo,
                projectId, documentId, "PG-REQ-UNIQ", "UNIQUE 테스트",
                "중복 삽입 검증용",
                RequirementCategory.SECURITY, true, false, false, FactLevel.FACT);

        TestHelper.insertInsight(insightRepo, reqId, "첫 번째 인사이트", "해석1", false, FactLevel.FACT);

        // 동일 requirementId로 두 번째 insight 삽입 시도 → 예외
        Assertions.assertThrows(Exception.class, () -> {
            TestHelper.insertInsight(insightRepo, reqId, "두 번째 인사이트", "해석2", false, FactLevel.FACT);
            insightRepo.flush();
        });
    }

    @Test @Order(31) @DisplayName("[PG] NOT NULL 제약: Requirement.originalText 누락 → 예외")
    void notNullConstraint() {
        Assertions.assertThrows(Exception.class, () -> {
            Requirement req = Requirement.builder()
                    .projectId(projectId)
                    .documentId(documentId)
                    .requirementCode("PG-REQ-NULL")
                    .title("NOT NULL 테스트")
                    .originalText(null) // NOT NULL 위반
                    .category(RequirementCategory.SECURITY)
                    .mandatoryFlag(true)
                    .evidenceRequiredFlag(false)
                    .queryNeeded(false)
                    .factLevel(FactLevel.FACT)
                    .build();
            requirementRepo.saveAndFlush(req);
        });
    }

    // ━━━ 5. 전체 API가 PostgreSQL에서도 동작하는지 확인 ━━━━━━━━━━━━━━

    @Test @Order(40) @DisplayName("[PG] 프로젝트 목록 API → PostgreSQL 쿼리 정상")
    void projectListApi() throws Exception {
        mvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].name").value("PostgreSQL 테스트 프로젝트"));
    }

    @Test @Order(41) @DisplayName("[PG] 체크리스트 생성 + 항목 추가 API → PostgreSQL")
    void checklistApiOnPostgres() throws Exception {
        String clId = h.createChecklist(token, projectId, "PG API 체크리스트", "SUBMISSION");
        assertThat(clId).isNotBlank();

        String itemId = h.createChecklistItem(token, projectId, clId, "PG 항목", true, "MEDIUM");
        assertThat(itemId).isNotBlank();
    }

    @Test @Order(42) @DisplayName("[PG] 질의 생성 API → PostgreSQL")
    void inquiryApiOnPostgres() throws Exception {
        String inqId = h.createInquiry(token, projectId, "PG 질의", "PostgreSQL 호환성 확인");
        assertThat(inqId).isNotBlank();

        // 상세 조회 확인
        mvc.perform(get("/api/v1/projects/" + projectId + "/inquiries/" + inqId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("PG 질의"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test @Order(43) @DisplayName("[PG] 요구사항 목록 API (필터 포함) → PostgreSQL")
    void requirementListWithFilterOnPostgres() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements?category=SECURITY")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test @Order(44) @DisplayName("[PG] 요구사항 품질 통계 API → PostgreSQL")
    void qualityStatsOnPostgres() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectId + "/requirements/quality-stats")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

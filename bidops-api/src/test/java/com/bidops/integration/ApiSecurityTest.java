package com.bidops.integration;

import com.bidops.auth.JwtTokenProvider;
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
 * 인증/인가 보안 테스트.
 * test 프로파일에서 /api/** 인증 면제가 없어야 함을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtTokenProvider jwtProvider;

    static String tokenA;
    static String tokenB;
    static String projectIdA;
    private TestHelper h;

    @BeforeEach
    void initHelper() { h = new TestHelper(mvc, om); }

    // ─── Setup: 두 사용자 생성 ──────────────────────────────────────
    @Test @Order(1) @DisplayName("Setup: 사용자 A 가입")
    void setupUserA() throws Exception {
        tokenA = h.signup("사용자A", "usera@test.com", "password123", "조직A");
    }

    @Test @Order(2) @DisplayName("Setup: 사용자 B 가입 (다른 조직)")
    void setupUserB() throws Exception {
        tokenB = h.signup("사용자B", "userb@test.com", "password123", "조직B");
    }

    @Test @Order(3) @DisplayName("Setup: 사용자 A가 프로젝트 생성")
    void setupProject() throws Exception {
        projectIdA = h.createProject(tokenA, "A의 프로젝트");
    }

    // ─── 인증 없이 보호 API 접근 → 401 Unauthorized ────────────────
    @Test @Order(10) @DisplayName("GET /projects 토큰 없음 → 401")
    void listProjectsNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(11) @DisplayName("GET /projects/{id} 토큰 없음 → 401")
    void getProjectNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(12) @DisplayName("GET /documents 토큰 없음 → 401")
    void listDocumentsNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(13) @DisplayName("GET /requirements 토큰 없음 → 401")
    void listRequirementsNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/requirements"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(14) @DisplayName("GET /checklists 토큰 없음 → 401")
    void listChecklistsNoToken() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/checklists"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(15) @DisplayName("POST /projects 토큰 없음 → 401")
    void createProjectNoToken() throws Exception {
        mvc.perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"무단생성","client_name":"c","business_name":"b"}
                """))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(16) @DisplayName("잘못된 JWT → 401")
    void invalidToken() throws Exception {
        mvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    // ─── 다른 사용자/조직의 프로젝트 접근 → 403 또는 빈 결과 ──────────
    @Test @Order(20) @DisplayName("사용자 B가 A의 프로젝트 상세 조회 → 403/404")
    void crossOrgProjectAccess() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA)
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(21) @DisplayName("사용자 B가 A의 문서 목록 조회 → 403/404")
    void crossOrgDocumentAccess() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/documents")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(22) @DisplayName("사용자 B가 A의 요구사항 목록 조회 → 403/404")
    void crossOrgRequirementAccess() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/requirements")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(23) @DisplayName("사용자 B가 A의 체크리스트 목록 조회 → 403/404")
    void crossOrgChecklistAccess() throws Exception {
        mvc.perform(get("/api/v1/projects/" + projectIdA + "/checklists")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().is4xxClientError());
    }

    @Test @Order(24) @DisplayName("사용자 B가 A의 프로젝트 수정 시도 → 403/404")
    void crossOrgProjectEdit() throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectIdA)
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"무단수정"}
                """))
                .andExpect(status().is4xxClientError());
    }

    // ─── 공개 엔드포인트는 토큰 없이도 접근 가능 ──────────────────────
    @Test @Order(30) @DisplayName("POST /auth/login은 토큰 없이 접근 가능")
    void loginPublic() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"usera@test.com","password":"password123"}
                """))
                .andExpect(status().isOk());
    }

    @Test @Order(31) @DisplayName("POST /auth/signup은 토큰 없이 접근 가능")
    void signupPublic() throws Exception {
        // 이미 가입된 이메일이라 400/409지만, 403이 아님을 확인
        int status = mvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"중복","email":"usera@test.com","password":"pass123","organization_name":"조직"}
                """))
                .andReturn().getResponse().getStatus();
        // 400 (validation) or 409 (conflict) — NOT 403
        org.assertj.core.api.Assertions.assertThat(status)
                .as("signup should not return 403").isNotEqualTo(403);
    }
}

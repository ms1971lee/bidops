package com.bidops.domain.project.service;

import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.project.entity.Project;
import com.bidops.domain.project.entity.ProjectMember;
import com.bidops.domain.project.entity.ProjectMember.ProjectRole;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.repository.ProjectMemberRepository;
import com.bidops.domain.project.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectAuthorizationServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock ProjectMemberRepository memberRepository;
    @InjectMocks ProjectAuthorizationService sut;

    static final String PROJECT_ID = "proj-1";
    static final String USER_ID = "user-1";

    private void givenProjectExists() {
        given(projectRepository.findByIdAndDeletedFalse(PROJECT_ID))
                .willReturn(Optional.of(Project.builder().build()));
    }

    private void givenMemberWithRole(ProjectRole role) {
        givenProjectExists();
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .willReturn(Optional.of(ProjectMember.builder()
                        .projectId(PROJECT_ID).userId(USER_ID).projectRole(role).build()));
    }

    @Test
    @DisplayName("프로젝트 미존재 시 404")
    void notFoundProject() {
        given(projectRepository.findByIdAndDeletedFalse("no-proj")).willReturn(Optional.empty());
        assertThatThrownBy(() -> sut.requirePermission("no-proj", USER_ID, ProjectPermission.PROJECT_VIEW))
                .isInstanceOf(BidOpsException.class)
                .extracting(e -> ((BidOpsException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("비멤버 → 403")
    void notAMember() {
        givenProjectExists();
        given(memberRepository.findByProjectIdAndUserId(PROJECT_ID, USER_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> sut.requirePermission(PROJECT_ID, USER_ID, ProjectPermission.PROJECT_VIEW))
                .isInstanceOf(BidOpsException.class)
                .extracting(e -> ((BidOpsException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── OWNER ──
    @Nested @DisplayName("OWNER")
    class OwnerTests {
        @BeforeEach void setUp() { givenMemberWithRole(ProjectRole.OWNER); }

        @ParameterizedTest @EnumSource(ProjectPermission.class)
        @DisplayName("OWNER는 모든 권한 통과")
        void all(ProjectPermission p) {
            assertThatCode(() -> sut.requirePermission(PROJECT_ID, USER_ID, p)).doesNotThrowAnyException();
        }
    }

    // ── ADMIN ──
    @Nested @DisplayName("ADMIN")
    class AdminTests {
        @BeforeEach void setUp() { givenMemberWithRole(ProjectRole.ADMIN); }

        @ParameterizedTest
        @EnumSource(value = ProjectPermission.class, names = {
                "PROJECT_VIEW", "DOCUMENT_VIEW", "REQUIREMENT_VIEW",
                "CHECKLIST_VIEW", "INQUIRY_VIEW", "ANALYSIS_VIEW", "MEMBER_VIEW",
                "REQUIREMENT_APPROVE",
                "DOCUMENT_UPLOAD", "REQUIREMENT_EDIT", "CHECKLIST_EDIT", "INQUIRY_EDIT",
                "PROJECT_EDIT", "DOCUMENT_DELETE", "ANALYSIS_RUN"
        })
        @DisplayName("ADMIN 허용")
        void allowed(ProjectPermission p) {
            assertThatCode(() -> sut.requirePermission(PROJECT_ID, USER_ID, p)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = ProjectPermission.class, names = { "MEMBER_MANAGE" })
        @DisplayName("ADMIN → MEMBER_MANAGE 거부")
        void denied(ProjectPermission p) {
            assertThatThrownBy(() -> sut.requirePermission(PROJECT_ID, USER_ID, p))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── EDITOR ──
    @Nested @DisplayName("EDITOR")
    class EditorTests {
        @BeforeEach void setUp() { givenMemberWithRole(ProjectRole.EDITOR); }

        @ParameterizedTest
        @EnumSource(value = ProjectPermission.class, names = {
                "PROJECT_VIEW", "DOCUMENT_VIEW", "REQUIREMENT_VIEW",
                "CHECKLIST_VIEW", "INQUIRY_VIEW", "ANALYSIS_VIEW", "MEMBER_VIEW",
                "REQUIREMENT_APPROVE",
                "DOCUMENT_UPLOAD", "REQUIREMENT_EDIT", "CHECKLIST_EDIT", "INQUIRY_EDIT"
        })
        @DisplayName("EDITOR 허용")
        void allowed(ProjectPermission p) {
            assertThatCode(() -> sut.requirePermission(PROJECT_ID, USER_ID, p)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = ProjectPermission.class, names = {
                "PROJECT_EDIT", "MEMBER_MANAGE", "DOCUMENT_DELETE", "ANALYSIS_RUN"
        })
        @DisplayName("EDITOR → 관리 권한 거부")
        void denied(ProjectPermission p) {
            assertThatThrownBy(() -> sut.requirePermission(PROJECT_ID, USER_ID, p))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── REVIEWER ──
    @Nested @DisplayName("REVIEWER")
    class ReviewerTests {
        @BeforeEach void setUp() { givenMemberWithRole(ProjectRole.REVIEWER); }

        @ParameterizedTest
        @EnumSource(value = ProjectPermission.class, names = {
                "PROJECT_VIEW", "DOCUMENT_VIEW", "REQUIREMENT_VIEW",
                "CHECKLIST_VIEW", "INQUIRY_VIEW", "ANALYSIS_VIEW", "MEMBER_VIEW",
                "REQUIREMENT_APPROVE"
        })
        @DisplayName("REVIEWER 허용 (조회 + 검토)")
        void allowed(ProjectPermission p) {
            assertThatCode(() -> sut.requirePermission(PROJECT_ID, USER_ID, p)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = ProjectPermission.class, names = {
                "DOCUMENT_UPLOAD", "REQUIREMENT_EDIT", "CHECKLIST_EDIT", "INQUIRY_EDIT",
                "PROJECT_EDIT", "MEMBER_MANAGE", "DOCUMENT_DELETE", "ANALYSIS_RUN"
        })
        @DisplayName("REVIEWER → 편집/관리 거부")
        void denied(ProjectPermission p) {
            assertThatThrownBy(() -> sut.requirePermission(PROJECT_ID, USER_ID, p))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── VIEWER ──
    @Nested @DisplayName("VIEWER")
    class ViewerTests {
        @BeforeEach void setUp() { givenMemberWithRole(ProjectRole.VIEWER); }

        @ParameterizedTest
        @EnumSource(value = ProjectPermission.class, names = {
                "PROJECT_VIEW", "DOCUMENT_VIEW", "REQUIREMENT_VIEW",
                "CHECKLIST_VIEW", "INQUIRY_VIEW", "ANALYSIS_VIEW", "MEMBER_VIEW"
        })
        @DisplayName("VIEWER 조회만")
        void allowed(ProjectPermission p) {
            assertThatCode(() -> sut.requirePermission(PROJECT_ID, USER_ID, p)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = ProjectPermission.class, names = {
                "REQUIREMENT_APPROVE",
                "DOCUMENT_UPLOAD", "REQUIREMENT_EDIT", "CHECKLIST_EDIT", "INQUIRY_EDIT",
                "PROJECT_EDIT", "MEMBER_MANAGE", "DOCUMENT_DELETE", "ANALYSIS_RUN"
        })
        @DisplayName("VIEWER → 모든 수정 거부")
        void denied(ProjectPermission p) {
            assertThatThrownBy(() -> sut.requirePermission(PROJECT_ID, USER_ID, p))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}

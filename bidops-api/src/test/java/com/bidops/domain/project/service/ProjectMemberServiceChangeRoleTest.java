package com.bidops.domain.project.service;

import com.bidops.auth.User;
import com.bidops.auth.UserRepository;
import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.project.dto.MemberRoleChangeRequest;
import com.bidops.domain.project.dto.ProjectMemberDto;
import com.bidops.domain.project.entity.MemberRoleAuditLog;
import com.bidops.domain.project.entity.ProjectMember;
import com.bidops.domain.project.entity.ProjectMember.ProjectRole;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.repository.MemberRoleAuditLogRepository;
import com.bidops.domain.project.repository.ProjectMemberRepository;
import com.bidops.domain.project.service.impl.ProjectMemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceChangeRoleTest {

    @Mock ProjectMemberRepository memberRepository;
    @Mock UserRepository userRepository;
    @Mock ProjectAuthorizationService authorizationService;
    @Mock MemberRoleAuditLogRepository auditLogRepository;
    @Mock ProjectActivityService activityService;
    @InjectMocks ProjectMemberServiceImpl sut;

    static final String PROJECT_ID = "proj-1";
    static final String OWNER_USER_ID = "owner-1";
    static final String TARGET_USER_ID = "target-1";
    static final String MEMBER_ID = "member-1";

    private MemberRoleChangeRequest request(ProjectRole role) {
        // 리플렉션으로 @NoArgsConstructor DTO에 값 주입
        MemberRoleChangeRequest req = new MemberRoleChangeRequest();
        try {
            var field = MemberRoleChangeRequest.class.getDeclaredField("role");
            field.setAccessible(true);
            field.set(req, role);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return req;
    }

    private ProjectMember buildMember(String id, String userId, ProjectRole role) {
        return ProjectMember.builder()
                .id(id)
                .projectId(PROJECT_ID)
                .userId(userId)
                .projectRole(role)
                .build();
    }

    // ── 성공 케이스 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("성공")
    class Success {

        @Test
        @DisplayName("EDITOR → VIEWER 역할 변경 성공 + 감사로그 저장")
        void editorToViewer() {
            ProjectMember target = buildMember(MEMBER_ID, TARGET_USER_ID, ProjectRole.EDITOR);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(target));
            given(userRepository.findById(TARGET_USER_ID))
                    .willReturn(Optional.of(User.builder().id(TARGET_USER_ID).email("t@b.com").name("Target").build()));

            ProjectMemberDto result = sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.VIEWER));

            assertThat(result.getProjectRole()).isEqualTo(ProjectRole.VIEWER);
            assertThat(target.getProjectRole()).isEqualTo(ProjectRole.VIEWER);

            // 감사로그 검증
            ArgumentCaptor<MemberRoleAuditLog> captor = ArgumentCaptor.forClass(MemberRoleAuditLog.class);
            then(auditLogRepository).should().save(captor.capture());
            MemberRoleAuditLog log = captor.getValue();
            assertThat(log.getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(log.getTargetUserId()).isEqualTo(TARGET_USER_ID);
            assertThat(log.getChangedBy()).isEqualTo(OWNER_USER_ID);
            assertThat(log.getOldRole()).isEqualTo(ProjectRole.EDITOR);
            assertThat(log.getNewRole()).isEqualTo(ProjectRole.VIEWER);
        }

        @Test
        @DisplayName("VIEWER → EDITOR 승격 성공")
        void viewerToEditor() {
            ProjectMember target = buildMember(MEMBER_ID, TARGET_USER_ID, ProjectRole.VIEWER);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(target));
            given(userRepository.findById(TARGET_USER_ID))
                    .willReturn(Optional.of(User.builder().id(TARGET_USER_ID).email("t@b.com").name("Target").build()));

            ProjectMemberDto result = sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.EDITOR));

            assertThat(result.getProjectRole()).isEqualTo(ProjectRole.EDITOR);
        }

        @Test
        @DisplayName("EDITOR → OWNER 승격 성공")
        void editorToOwner() {
            ProjectMember target = buildMember(MEMBER_ID, TARGET_USER_ID, ProjectRole.EDITOR);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(target));
            given(userRepository.findById(TARGET_USER_ID))
                    .willReturn(Optional.of(User.builder().id(TARGET_USER_ID).email("t@b.com").name("Target").build()));

            ProjectMemberDto result = sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.OWNER));

            assertThat(result.getProjectRole()).isEqualTo(ProjectRole.OWNER);
        }

        @Test
        @DisplayName("OWNER 2명일 때 한 명 강등 성공")
        void demoteOwnerWhenMultipleOwners() {
            ProjectMember target = buildMember(MEMBER_ID, TARGET_USER_ID, ProjectRole.OWNER);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(target));
            given(memberRepository.countByProjectIdAndProjectRole(PROJECT_ID, ProjectRole.OWNER)).willReturn(2L);
            given(userRepository.findById(TARGET_USER_ID))
                    .willReturn(Optional.of(User.builder().id(TARGET_USER_ID).email("t@b.com").name("Target").build()));

            ProjectMemberDto result = sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.EDITOR));

            assertThat(result.getProjectRole()).isEqualTo(ProjectRole.EDITOR);
        }
    }

    // ── 실패 케이스 ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("실패")
    class Failure {

        @Test
        @DisplayName("OWNER가 아닌 사용자 → 403")
        void nonOwnerForbidden() {
            willThrow(BidOpsException.forbidden())
                    .given(authorizationService)
                    .requirePermission(PROJECT_ID, "editor-user", ProjectPermission.MEMBER_MANAGE);

            assertThatThrownBy(() -> sut.changeRole("editor-user", PROJECT_ID, MEMBER_ID, request(ProjectRole.VIEWER)))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("존재하지 않는 멤버 → 404")
        void memberNotFound() {
            given(memberRepository.findById("no-member")).willReturn(Optional.empty());

            assertThatThrownBy(() -> sut.changeRole(OWNER_USER_ID, PROJECT_ID, "no-member", request(ProjectRole.VIEWER)))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("다른 프로젝트 멤버 → 404")
        void memberFromDifferentProject() {
            ProjectMember otherProjectMember = ProjectMember.builder()
                    .id(MEMBER_ID)
                    .projectId("other-project")
                    .userId(TARGET_USER_ID)
                    .projectRole(ProjectRole.EDITOR)
                    .build();
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(otherProjectMember));

            assertThatThrownBy(() -> sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.VIEWER)))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("자기 자신 역할 변경 → 400")
        void cannotChangeSelf() {
            ProjectMember self = buildMember(MEMBER_ID, OWNER_USER_ID, ProjectRole.OWNER);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(self));

            assertThatThrownBy(() -> sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.EDITOR)))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("동일 역할로 변경 → 400")
        void sameRole() {
            ProjectMember target = buildMember(MEMBER_ID, TARGET_USER_ID, ProjectRole.EDITOR);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(target));

            assertThatThrownBy(() -> sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.EDITOR)))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("마지막 OWNER 강등 → 400")
        void cannotDemoteLastOwner() {
            ProjectMember target = buildMember(MEMBER_ID, TARGET_USER_ID, ProjectRole.OWNER);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(target));
            given(memberRepository.countByProjectIdAndProjectRole(PROJECT_ID, ProjectRole.OWNER)).willReturn(1L);

            assertThatThrownBy(() -> sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.EDITOR)))
                    .isInstanceOf(BidOpsException.class)
                    .extracting(e -> ((BidOpsException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("마지막 OWNER 강등 시 감사로그 저장 안됨")
        void noAuditLogOnDemoteLastOwner() {
            ProjectMember target = buildMember(MEMBER_ID, TARGET_USER_ID, ProjectRole.OWNER);
            given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(target));
            given(memberRepository.countByProjectIdAndProjectRole(PROJECT_ID, ProjectRole.OWNER)).willReturn(1L);

            assertThatThrownBy(() -> sut.changeRole(OWNER_USER_ID, PROJECT_ID, MEMBER_ID, request(ProjectRole.VIEWER)));

            then(auditLogRepository).shouldHaveNoInteractions();
        }
    }
}

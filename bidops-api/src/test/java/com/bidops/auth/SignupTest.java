package com.bidops.auth;

import com.bidops.auth.AuthController.SignupRequest;
import com.bidops.common.exception.BidOpsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class SignupTest {

    @Mock UserRepository userRepository;
    @Mock JwtTokenProvider tokenProvider;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks AuthController sut;

    private SignupRequest request(String name, String email, String password) {
        SignupRequest req = new SignupRequest();
        try {
            setField(req, "name", name);
            setField(req, "email", email);
            setField(req, "password", password);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return req;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Nested
    @DisplayName("성공")
    class Success {

        @Test
        @DisplayName("정상 가입 → 201, JWT 발급, User 저장")
        void signupSuccess() {
            given(userRepository.existsByEmail("new@test.com")).willReturn(false);
            given(passwordEncoder.encode("password123")).willReturn("$2a$hashed");
            given(userRepository.save(any(User.class))).willAnswer(inv -> {
                User u = inv.getArgument(0);
                setField(u, "id", "generated-id");
                return u;
            });
            given(tokenProvider.generate(eq("generated-id"), eq("new@test.com"), eq("홍길동"), any()))
                    .willReturn("jwt-token");

            var result = sut.signup(request("홍길동", "new@test.com", "password123"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getToken()).isEqualTo("jwt-token");
            assertThat(result.getData().getEmail()).isEqualTo("new@test.com");
            assertThat(result.getData().getName()).isEqualTo("홍길동");
            assertThat(result.getData().getUserId()).isEqualTo("generated-id");

            // User 저장 시 BCrypt 해시가 사용되었는지 확인
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userRepository).should().save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo("new@test.com");
            assertThat(saved.getName()).isEqualTo("홍길동");
            assertThat(saved.getPasswordHash()).isEqualTo("$2a$hashed");
            assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("실패")
    class Failure {

        @Test
        @DisplayName("이메일 중복 → 409 CONFLICT")
        void duplicateEmail() {
            given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

            assertThatThrownBy(() -> sut.signup(request("홍길동", "dup@test.com", "password123")))
                    .isInstanceOf(BidOpsException.class)
                    .satisfies(e -> {
                        BidOpsException ex = (BidOpsException) e;
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(ex.getCode()).isEqualTo("CONFLICT");
                    });

            then(userRepository).should(never()).save(any());
        }
    }
}

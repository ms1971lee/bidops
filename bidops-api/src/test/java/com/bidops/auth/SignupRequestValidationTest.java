package com.bidops.auth;

import com.bidops.auth.AuthController.SignupRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class SignupRequestValidationTest {

    static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private SignupRequest build(String name, String email, String password) {
        SignupRequest req = new SignupRequest();
        try {
            setField(req, "name", name);
            setField(req, "email", email);
            setField(req, "password", password);
            setField(req, "organizationName", "테스트조직");
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

    @Test
    @DisplayName("정상 입력 → 검증 통과")
    void validRequest() {
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validate(build("홍길동", "test@example.com", "password123"));
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("이름 누락 → 검증 실패")
    void nameMissing(String name) {
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validate(build(name, "test@example.com", "password123"));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("이메일 누락 → 검증 실패")
    void emailMissing(String email) {
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validate(build("홍길동", email, "password123"));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"notanemail", "missing@", "@nodomain", "spaces in@email.com"})
    @DisplayName("잘못된 이메일 형식 → 검증 실패")
    void invalidEmailFormat(String email) {
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validate(build("홍길동", email, "password123"));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("비밀번호 누락 → 검증 실패")
    void passwordMissing(String password) {
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validate(build("홍길동", "test@example.com", password));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("비밀번호 8자 미만 → 검증 실패")
    void passwordTooShort() {
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validate(build("홍길동", "test@example.com", "short"));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("이름 51자 초과 → 검증 실패")
    void nameTooLong() {
        String longName = "가".repeat(51);
        Set<ConstraintViolation<SignupRequest>> violations =
                validator.validate(build(longName, "test@example.com", "password123"));
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }
}

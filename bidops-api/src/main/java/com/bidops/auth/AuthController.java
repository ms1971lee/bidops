package com.bidops.auth;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ApiResponse;
import com.bidops.domain.organization.entity.Organization;
import com.bidops.domain.organization.repository.OrganizationRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;

    @PostMapping("/login")
    @Operation(summary = "로그인", operationId = "login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> BidOpsException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BidOpsException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String orgName = resolveOrgName(user.getOrganizationId());
        String token = tokenProvider.generate(user.getId(), user.getEmail(), user.getName(), user.getOrganizationId());
        return ApiResponse.ok(new LoginResponse(token, user.getId(), user.getEmail(), user.getName(),
                user.getOrganizationId(), orgName));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "회원가입", operationId = "signup")
    public ApiResponse<LoginResponse> signup(@RequestBody @Valid SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BidOpsException.conflict("이미 사용 중인 이메일입니다.");
        }

        Organization org = Organization.builder()
                .name(request.getOrganizationName())
                .build();
        organizationRepository.save(org);

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .organizationId(org.getId())
                .build();
        userRepository.save(user);

        String token = tokenProvider.generate(user.getId(), user.getEmail(), user.getName(), org.getId());
        return ApiResponse.ok(new LoginResponse(token, user.getId(), user.getEmail(), user.getName(),
                org.getId(), org.getName()));
    }

    @GetMapping("/me")
    @Operation(summary = "현재 사용자 조회", operationId = "getMe")
    public ApiResponse<MeResponse> me() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BidOpsException.notFound("사용자"));
        String orgName = resolveOrgName(user.getOrganizationId());
        return ApiResponse.ok(new MeResponse(user.getId(), user.getEmail(), user.getName(),
                user.getOrganizationId(), orgName));
    }

    private String resolveOrgName(String orgId) {
        if (orgId == null) return null;
        return organizationRepository.findById(orgId)
                .map(Organization::getName)
                .orElse(null);
    }

    // ── DTOs ────────────────────────────────────────────────────────
    @Getter @NoArgsConstructor
    public static class SignupRequest {
        @NotBlank(message = "이름은 필수입니다")
        @Size(max = 50, message = "이름은 50자 이내여야 합니다")
        private String name;

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이어야 합니다")
        private String email;

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다")
        private String password;

        @NotBlank(message = "조직명은 필수입니다")
        @Size(max = 200, message = "조직명은 200자 이내여야 합니다")
        @JsonProperty("organization_name")
        private String organizationName;
    }

    @Getter @NoArgsConstructor
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Getter
    public static class LoginResponse {
        private final String token;
        private final String userId;
        private final String email;
        private final String name;
        @JsonProperty("organization_id")
        private final String organizationId;
        @JsonProperty("organization_name")
        private final String organizationName;
        LoginResponse(String t, String u, String e, String n, String oi, String on) {
            token=t; userId=u; email=e; name=n; organizationId=oi; organizationName=on;
        }
    }

    @Getter
    public static class MeResponse {
        private final String userId;
        private final String email;
        private final String name;
        @JsonProperty("organization_id")
        private final String organizationId;
        @JsonProperty("organization_name")
        private final String organizationName;
        MeResponse(String u, String e, String n, String oi, String on) {
            userId=u; email=e; name=n; organizationId=oi; organizationName=on;
        }
    }
}

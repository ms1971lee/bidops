package com.bidops.auth;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/login")
    @Operation(summary = "로그인", operationId = "login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> BidOpsException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BidOpsException.badRequest("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = tokenProvider.generate(user.getId(), user.getEmail(), user.getName());
        return ApiResponse.ok(new LoginResponse(token, user.getId(), user.getEmail(), user.getName()));
    }

    @GetMapping("/me")
    @Operation(summary = "현재 사용자 조회", operationId = "getMe")
    public ApiResponse<MeResponse> me() {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BidOpsException.notFound("사용자"));
        return ApiResponse.ok(new MeResponse(user.getId(), user.getEmail(), user.getName()));
    }

    // ── DTOs ────────────────────────────────────────────────────────
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
        LoginResponse(String t, String u, String e, String n) { token=t; userId=u; email=e; name=n; }
    }

    @Getter
    public static class MeResponse {
        private final String userId;
        private final String email;
        private final String name;
        MeResponse(String u, String e, String n) { userId=u; email=e; name=n; }
    }
}

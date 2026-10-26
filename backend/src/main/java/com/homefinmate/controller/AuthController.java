package com.homefinmate.controller;

import com.homefinmate.common.ApiResponse;
import com.homefinmate.common.TraceIds;
import com.homefinmate.dto.auth.LoginRequest;
import com.homefinmate.dto.auth.LoginResponse;
import com.homefinmate.dto.auth.MeResponse;
import com.homefinmate.dto.auth.SignupRequest;
import com.homefinmate.dto.auth.SignupResponse;
import com.homefinmate.security.AuthCookies;
import com.homefinmate.service.AuthService;
import com.homefinmate.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

// 인증 API (/api/auth): 회원가입, 로그인
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIds.current()));
    }

    // 로그인 (성공 시 hfm_session 쿠키 발급, 본문에는 이메일·역할·만료 시각)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.session(response.getToken()).toString())
                .body(ApiResponse.success(response, TraceIds.current()));
    }

    // 현재 로그인 사용자 이메일·역할 (DB 기준, 로그인하지 않았으면 401)
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(Authentication authentication) {
        String email = ((AuthUser) authentication.getPrincipal()).email();
        String role = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).findFirst().orElse("USER");
        return ResponseEntity.ok(ApiResponse.success(new MeResponse(email, role), TraceIds.current()));
    }

    // 로그아웃 (hfm_session 쿠키 삭제)
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.clear().toString())
                .body(ApiResponse.success(null, TraceIds.current()));
    }
}

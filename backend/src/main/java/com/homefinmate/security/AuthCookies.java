package com.homefinmate.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

// 로그인 쿠키(hfm_session) 만들기·지우기·읽기
@Component
public class AuthCookies {

    public static final String SESSION = "hfm_session";

    private final boolean secure;
    private final Duration maxAge;

    public AuthCookies(@Value("${app.auth.cookie-secure:true}") boolean secure,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secure = secure;
        this.maxAge = Duration.ofMillis(expirationMs);
    }

    // 로그인 성공 시 내려 줄 쿠키 (토큰 만료와 같은 시간 동안 유지)
    public ResponseCookie session(String token) {
        return base(token).maxAge(maxAge).build();
    }

    // 로그아웃 시 쿠키 삭제 (같은 이름·경로에 Max-Age=0)
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    // 요청의 로그인 쿠키 값 (없으면 null)
    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies).filter(c -> SESSION.equals(c.getName())).map(Cookie::getValue)
                .filter(v -> v != null && !v.isEmpty()).findFirst().orElse(null);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(SESSION, value).httpOnly(true).secure(secure).sameSite("Strict").path("/api");
    }
}

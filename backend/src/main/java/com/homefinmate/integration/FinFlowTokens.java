package com.homefinmate.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;

// FinFlow에 보내는 서비스 토큰 발급 (HS256, iss=homefin-mate, aud=finflow, 유효 기간 1분)
// 비밀키 FINFLOW_TOKEN_SECRET은 FinFlow .env의 SERVICE_TOKEN_SECRET과 같은 값
@Component
public class FinFlowTokens {

    public static final String ISSUER = "homefin-mate";
    public static final String AUDIENCE = "finflow";
    // FinFlow는 5분까지 받지만, 호출마다 새로 만들므로 짧게
    public static final long LIFETIME_MS = 60 * 1000L;

    private final SecretKey key;

    // 키가 32바이트보다 짧으면 시작 실패 (jjwt WeakKeyException)
    // 로그인 JWT 키·RiskWatch 토큰 키와 같아도 시작 실패: 한 키가 새면 다른 서버 토큰까지 만들 수 있음 (메시지에 키 값 없음)
    public FinFlowTokens(@Value("${app.finflow.token-secret}") String secret,
            @Value("${app.jwt.secret}") String userJwtSecret,
            @Value("${app.riskwatch.token-secret}") String riskWatchSecret) {
        if (Stream.of(secret, userJwtSecret, riskWatchSecret).distinct().count() < 3) {
            throw new IllegalStateException(
                    "FINFLOW_TOKEN_SECRET must differ from JWT_SECRET and RISKWATCH_TOKEN_SECRET");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(LIFETIME_MS)))
                .signWith(key)
                .compact();
    }
}

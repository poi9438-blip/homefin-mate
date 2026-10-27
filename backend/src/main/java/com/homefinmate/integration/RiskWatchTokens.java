package com.homefinmate.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

// RiskWatch에 보내는 서비스 토큰 발급 (HS256, iss=homefin-mate, aud=riskwatch, 유효 기간 1분)
@Component
public class RiskWatchTokens {

    public static final String ISSUER = "homefin-mate";
    public static final String AUDIENCE = "riskwatch";
    // RiskWatch는 5분까지 받지만, 호출마다 새로 만들므로 짧게
    public static final long LIFETIME_MS = 60 * 1000L;

    private final SecretKey key;

    // 키가 32바이트보다 짧으면 시작 실패 (jjwt WeakKeyException), JWT_SECRET과 같아도 시작 실패 (메시지에 키 값
    // 없음)
    public RiskWatchTokens(@Value("${app.riskwatch.token-secret}") String secret,
            @Value("${app.jwt.secret}") String userJwtSecret) {
        if (secret.equals(userJwtSecret)) {
            throw new IllegalStateException("RISKWATCH_TOKEN_SECRET must differ from JWT_SECRET");
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

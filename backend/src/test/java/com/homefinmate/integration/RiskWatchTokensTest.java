package com.homefinmate.integration;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// RiskWatch 서비스 토큰 비밀키 시작 검사 (서버 없이 생성자만)
class RiskWatchTokensTest {

    private static final String TOKEN = "RiskWatch-Token-Test-Secret-32bytes-long!!";
    private static final String JWT = "User-JWT-Test-Secret-32bytes-long!!!!!!!!!";

    @Test
    @DisplayName("[TC-RISK-207] RISKWATCH_TOKEN_SECRET이 JWT_SECRET과 같거나 32바이트보다 짧으면 시작 실패, 메시지에 키 값 없음")
    void secretMustDifferAndBeLongEnough() {
        assertThatCode(() -> new RiskWatchTokens(TOKEN, JWT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new RiskWatchTokens(JWT, JWT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ")
                .hasMessageNotContaining(JWT);
        assertThatThrownBy(() -> new RiskWatchTokens("short-riskwatch-secret", JWT))
                .isInstanceOf(WeakKeyException.class);
    }
}

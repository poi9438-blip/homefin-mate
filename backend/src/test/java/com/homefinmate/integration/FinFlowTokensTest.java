package com.homefinmate.integration;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// FinFlow 서비스 토큰 비밀키 시작 검사 (서버 없이 생성자만)
class FinFlowTokensTest {

    private static final String FINFLOW = "FinFlow-Token-Test-Secret-32bytes-long!!!!";
    private static final String JWT = "User-JWT-Test-Secret-32bytes-long!!!!!!!!!";
    private static final String RISKWATCH = "RiskWatch-Token-Test-Secret-32bytes-long!!";

    @Test
    @DisplayName("[TC-APPLY-204] FINFLOW_TOKEN_SECRET이 JWT_SECRET·RISKWATCH_TOKEN_SECRET과 같거나 32바이트보다 짧으면 시작 실패, 메시지에 키 값 없음")
    void secretMustDifferAndBeLongEnough() {
        assertThatCode(() -> new FinFlowTokens(FINFLOW, JWT, RISKWATCH)).doesNotThrowAnyException();
        for (String[] same : new String[][] { { JWT, JWT, RISKWATCH }, { RISKWATCH, JWT, RISKWATCH } }) {
            assertThatThrownBy(() -> new FinFlowTokens(same[0], same[1], same[2]))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("must differ")
                    .hasMessageNotContaining(same[0]);
        }
        assertThatThrownBy(() -> new FinFlowTokens("short-finflow-secret", JWT, RISKWATCH))
                .isInstanceOf(WeakKeyException.class);
    }
}

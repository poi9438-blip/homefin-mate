# 소득 암호화 (TC-ENC)

대상: `EncryptionUtil` — AES-256-GCM. 저장 형식은 `Base64(IV 12바이트 + 암호문 + 인증 태그 16바이트)`이고, 키는 `.env`의 `ENCRYPTION_KEY`입니다.

- **암호화하는 값:** 회원 소득(`users.encrypted_income`), 계산 기록의 DTI(`loan_simulations.dti_encrypted`, JPA 변환기 `EncryptedDecimalConverter`). DTI까지 암호화해서 월 상환액 ÷ DTI로 소득을 역산할 수 없습니다(#31).
- **해시하는 값:** 로그인 실패 기록의 이메일(`login_failure_logs.email_hash`, HMAC-SHA256, `EmailHasher`). 시도 제한에서 같은 이메일인지만 비교합니다(#33).
- 예전 TC-ENC-302(계산 기록·로그로 소득 역산)는 TC-DATA-011·012와 TC-ANLZ-103으로 대체했습니다.

암호화 방식 변경이나 기존 암호문 일괄 변환은 이 테스트의 범위가 아닙니다.

| ID         | 분류 | 확인 내용                                                               | 기대 결과                                                                                           | 위험 | 자동화                                                    | 관련                |
| ---------- | ---- | ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ---- | --------------------------------------------------------- | ------------------- |
| TC-ENC-001 | 정상 | 암호화 후 복호화                                                        | 원래 값, 암호문 ≠ 평문                                                                              | P1   | EncryptionUtilTest                                        |                     |
| TC-ENC-002 | 보안 | 같은 값을 두 번 암호화                                                  | 결과가 다름                                                                                         | P1   | EncryptionUtilTest                                        |                     |
| TC-ENC-003 | 보안 | 다른 키로 복호화                                                        | "Decryption failed"                                                                                 | P1   | EncryptionUtilTest                                        |                     |
| TC-ENC-004 | 보안 | 이메일 HMAC (`EmailHasher`)                                             | 같은 이메일(대소문자·공백 무시)은 같은 값, 이메일 미포함, 키가 다르면 다른 값                       | P2   | EmailHasherTest                                           | #33                 |
| TC-ENC-005 | 보안 | 암호화 키가 UTF-8 32바이트가 아님 (0·8·31·33바이트, 한글 11자=33바이트) | 서버 시작 실패, 메시지에 바이트 수만 있고 키 값은 없음 (예전에는 0으로 채우거나 잘라서 그대로 동작) | P1   | EncryptionUtilTest                                        | #29                 |
| TC-ENC-201 | 보안 | 암호문 마지막 바이트 변조                                               | "Decryption failed" (GCM 무결성)                                                                    | P1   | EncryptionUtilTest                                        |                     |
| TC-ENC-202 | 예외 | Base64가 아닌 값, IV보다 짧은 값, 빈 값                                 | 같은 메시지 "Decryption failed" (내부 예외 노출 없음)                                               | P2   | EncryptionUtilTest                                        |                     |
| TC-ENC-203 | 보안 | 암호문 길이와 IV                                                        | 12 + 평문 + 16바이트, IV가 매번 다름                                                                | P1   | EncryptionUtilTest                                        |                     |
| TC-ENC-301 | 보안 | (폐기) 32바이트가 아닌 키                                               | 시작 시 거부 (현재 0으로 채우거나 잘라서 사용)                                                      | P2   | 폐기, 자동화 안 함: TC-ENC-005로 대체                     | #29                 |
| TC-ENC-302 | 보안 | (폐기) 계산 기록·로그로 소득 역산                                       | 역산 불가 (현재 월 상환액 ÷ DTI = 소득, 기존 대출 이자가 없을 때)                                   | P1   | 폐기, 자동화 안 함: TC-DATA-011·012, TC-ANLZ-103으로 대체 | #31, #83, 확인 필요 |

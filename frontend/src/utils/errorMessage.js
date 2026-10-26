// 알려진 서버 메시지 → 한국어
const KNOWN_MESSAGES = {
  "Invalid credentials": "이메일 또는 비밀번호가 올바르지 않습니다.",
  "Too many login attempts. Try again later":
    "로그인 시도가 너무 많습니다. 15분 뒤에 다시 시도해 주세요.",
  "Email already registered": "이미 가입된 이메일입니다.",
};

// 상태 코드별 한국어 (로그인 실패 401은 위 메시지로 따로 처리)
const STATUS_MESSAGES = {
  401: "로그인이 필요합니다.",
  403: "이 기능을 사용할 권한이 없습니다.",
};

// 화면에 보여 줄 오류 문구 (5xx·연결 실패는 화면 문구, 알려진 메시지·401·403은 한국어, 그 밖의 4xx는 서버 메시지)
export function errorMessage(err, fallback) {
  const status = err?.response?.status;
  if (!status || status >= 500) return fallback;
  const message = err.response.data?.message;
  return (
    KNOWN_MESSAGES[message] || STATUS_MESSAGES[status] || message || fallback
  );
}

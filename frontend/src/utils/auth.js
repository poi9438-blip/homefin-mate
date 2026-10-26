// 로그인 상태(만료 시각) 저장·확인·삭제 (토큰 자체는 HttpOnly 쿠키라 화면에 없음)

const EXPIRES_KEY = "hfm_session_expires_at";
// 예전 버전이 저장한 키 (로그아웃·로그인 때 삭제만)
const LEGACY_KEYS = ["hfm_token", "hfm_user"];

// 로그인 성공 시 만료 시각(epoch 밀리초) 저장, 예전 토큰 저장 흔적은 삭제
export function setSession(expiresAt) {
  LEGACY_KEYS.forEach((key) => localStorage.removeItem(key));
  localStorage.setItem(EXPIRES_KEY, String(expiresAt));
}

// 로그인 상태 정보·예전 토큰·입력 임시 저장 삭제 (로그아웃, 401 응답 시)
export function clearSession() {
  localStorage.removeItem(EXPIRES_KEY);
  LEGACY_KEYS.forEach((key) => localStorage.removeItem(key));
  Object.keys(localStorage)
    .filter((key) => key.startsWith("hfm_draft_"))
    .forEach((key) => localStorage.removeItem(key));
}

// 만료 시각이 지나지 않았으면 로그인 상태 (지났으면 정리)
export function isAuthenticated() {
  const expiresAt = Number(localStorage.getItem(EXPIRES_KEY));
  if (!expiresAt || expiresAt <= Date.now()) {
    if (expiresAt) clearSession();
    return false;
  }
  return true;
}

// 로그인 토큰을 localStorage에 저장/조회

const TOKEN_KEY = "hfm_token";
// 예전 버전이 저장한 사용자 정보 키 (로그아웃 때 삭제만)
const LEGACY_USER_KEY = "hfm_user";

// 저장된 토큰 꺼내기 (없으면 null)
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

// 토큰 저장 (로그인 성공 시)
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

// 토큰·예전 사용자 정보·입력 임시 저장 삭제 (로그아웃, 401 응답 시)
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(LEGACY_USER_KEY);
  Object.keys(localStorage)
    .filter((key) => key.startsWith("hfm_draft_"))
    .forEach((key) => localStorage.removeItem(key));
}

// 토큰의 만료 시각(ms), 읽을 수 없으면 null
export function tokenExpiresAt(token) {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const { exp } = JSON.parse(
      atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, "=")),
    );
    return typeof exp === "number" ? exp * 1000 : null;
  } catch {
    return null;
  }
}

// 토큰이 있고 만료 시각이 지나지 않았으면 로그인 상태 (만료 토큰은 삭제, 최종 판단은 서버)
export function isAuthenticated() {
  const token = getToken();
  if (!token) return false;
  const expiresAt = tokenExpiresAt(token);
  if (expiresAt !== null && expiresAt <= Date.now()) {
    removeToken();
    return false;
  }
  return true;
}

// 로그인 토큰과 사용자 정보를 localStorage에 저장/조회

const TOKEN_KEY = "hfm_token";
const USER_KEY = "hfm_user";

// 저장된 토큰 꺼내기 (없으면 null)
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

// 토큰 저장 (로그인 성공 시)
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

// 토큰과 사용자 정보 삭제 (로그아웃, 401 응답 시)
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

// 저장된 사용자 정보 꺼내기 (없거나 깨졌으면 null)
export function getUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

// 사용자 정보 저장 (객체 => JSON 문자열)
export function setUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

// 토큰이 있으면 로그인 상태로 간주 (만료 여부는 서버가 판단)
export function isAuthenticated() {
  return !!getToken();
}

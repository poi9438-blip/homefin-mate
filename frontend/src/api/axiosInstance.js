import axios from "axios";
import { getToken, removeToken } from "../utils/auth";
import { generateTraceId } from "../utils/traceId";

// 공통 axios 설정
const axiosInstance = axios.create({
  // API 기본 경로 (주소는 없음 => Vite proxy / nginx가 백엔드로 전달)
  baseURL: "/api",
  timeout: 15000,
  headers: {
    "Content-Type": "application/json",
  },
});

// 모든 요청에 토큰과 traceId 헤더 추가 (axiosInstance로 요청을 보내기 직전에 이 함수를 먼저 실행)
axiosInstance.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers["X-Trace-Id"] = generateTraceId();
  return config;
});

// 토큰 만료로 처리하지 않을 요청 (로그인 실패 401은 화면에서 문구로 보여 줌)
const SKIP_401_REDIRECT = ["/auth/login"];

// 401(인증 만료) 응답이면 토큰 삭제 후 로그인 화면으로 이동 (로그인 요청은 제외)
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginRequest = SKIP_401_REDIRECT.includes(error.config?.url);
    if (error.response?.status === 401 && !isLoginRequest) {
      removeToken();
      // 컴포넌트 밖이라 useNavigate 대신 페이지 이동 (상태 초기화 효과)
      window.location.href = "/login";
    }
    return Promise.reject(error);
  },
);

export default axiosInstance;

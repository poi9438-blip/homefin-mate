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

// 401(인증 만료) 응답이면 토큰 삭제 후 로그인 화면으로 이동
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      removeToken();
      // 컴포넌트 밖이라 useNavigate 대신 페이지 이동 (상태 초기화 효과)
      window.location.href = "/login";
    }
    return Promise.reject(error);
  },
);

export default axiosInstance;

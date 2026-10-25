import axiosInstance from "./axiosInstance";

// 관리자 API (기록 조회)
export const adminApi = {
  // 감사 기록 / 로그인 실패 / 이용 기록 / 연동 실패 (각 최근 100건)
  getAuditLogs: () => axiosInstance.get("/admin/audit-logs"),

  getLoginFailures: () => axiosInstance.get("/admin/login-failures"),

  // 이메일로 로그인 실패 검색 (최근 100건)
  searchLoginFailures: (email) =>
    axiosInstance.post("/admin/login-failures/search", { email }),

  getUserActivity: () => axiosInstance.get("/admin/user-activity"),

  getIntegrationFailures: () =>
    axiosInstance.get("/admin/integration-failures"),
};

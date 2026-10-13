import axiosInstance from "./axiosInstance";

// 관리자 API (기록 조회)
export const adminApi = {
  // 사용자 목록
  listUsers: () => axiosInstance.get("/admin/users"),

  // 감사 기록 / 로그인 실패 / 이용 기록 / 연동 실패 (각 최근 100건)
  getAuditLogs: () => axiosInstance.get("/admin/audit-logs"),

  getLoginFailures: () => axiosInstance.get("/admin/login-failures"),

  getUserActivity: () => axiosInstance.get("/admin/user-activity"),

  getIntegrationFailures: () =>
    axiosInstance.get("/admin/integration-failures"),
};

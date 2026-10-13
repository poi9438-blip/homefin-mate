import axiosInstance from "./axiosInstance";

// 리포트 API (요약, 생성)
export const reportApi = {
  // 내 요약 리포트
  getSummary: () => axiosInstance.get("/report/summary"),

  // 리포트 생성
  generateReport: (reportType) =>
    axiosInstance.post("/report/generate", null, { params: { reportType } }),
};

import axiosInstance from "./axiosInstance";

// 지역 분석 API
export const analysisApi = {
  // 지역 분석 조회 (로그인 불필요)
  getRegionAnalysis: (region) =>
    axiosInstance.get("/analysis/region", { params: { region } }),
};

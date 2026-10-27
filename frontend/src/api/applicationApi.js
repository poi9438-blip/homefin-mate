import axiosInstance from "./axiosInstance";

export const applicationApi = {
  // 대출 신청 (FinFlow 접수). 같은 신청을 다시 보낼 때는 같은 key를 써야 신청이 하나만 생김
  apply: (body, idempotencyKey) =>
    axiosInstance.post("/applications", body, {
      headers: { "Idempotency-Key": idempotencyKey },
    }),

  // 내 신청 최신 20건 (상태는 FinFlow에서)
  list: () => axiosInstance.get("/applications"),

  // 내 신청 취소
  cancel: (applicationId) => axiosInstance.post(`/applications/${applicationId}/cancel`),
};

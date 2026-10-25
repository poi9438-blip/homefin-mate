// DTI 판정 기준 (서버 DtiRule.LIMIT과 같은 값)
export const DTI_LIMIT = 0.6;

// 전환 전 기록인지 (기존 대출 이자 미포함)
export const isNewLoanOnly = (s) => s?.dtiBasis === "NEW_LOAN_ONLY";

export const NEW_LOAN_ONLY_NOTE = "기존 대출 이자 미포함 (전환 전 기록)";

// 판정별 표시 (적정 / 초과 / 판단 불가), 차트 색도 같은 기준
const DTI_STATUS_VIEW = {
  ACCEPTABLE: {
    label: "적정",
    chartColor: "#22c55e",
    badge: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  },
  HIGH: {
    label: "초과",
    chartColor: "#ef4444",
    badge: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  },
  UNKNOWN: {
    label: "판단 불가",
    chartColor: "#9ca3af",
    badge: "bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-200",
  },
};

export const dtiStatusView = (status) =>
  DTI_STATUS_VIEW[status] ?? DTI_STATUS_VIEW.UNKNOWN;

// DTI 판정 기준 (서버 DtiRule.LIMIT과 같은 값)
export const DTI_LIMIT = 0.6;

// 전환 전 기록인지 (기존 대출 이자 미포함)
export const isNewLoanOnly = (s) => s?.dtiBasis === "NEW_LOAN_ONLY";

export const NEW_LOAN_ONLY_NOTE = "기존 대출 이자 미포함 (전환 전 기록)";

// 정책대출 판정용 선택 입력 (대출 화면·정책 화면 공통)
export const POLICY_INPUT_DEFAULTS = {
  housePrice: "",
  householdAnnualIncome: "",
  firstHome: "",
  newlywed: "",
  childCount: "",
};

// '예/아니오/모름' 선택값 → true / false / undefined(모름, 서버에서 확인 필요로 판정)
export const triState = (v) =>
  v === "true" ? true : v === "false" ? false : undefined;

// 빈 입력은 보내지 않음(undefined), 값이 있으면 숫자로
export const numOrUndefined = (v) =>
  v === "" || v == null ? undefined : Number(v);

// 요청에 넣을 정책대출 판정용 값
export const policyInputPayload = (form) => ({
  housePrice: numOrUndefined(form.housePrice),
  householdAnnualIncome: numOrUndefined(form.householdAnnualIncome),
  firstHome: triState(form.firstHome),
  newlywed: triState(form.newlywed),
  childCount: numOrUndefined(form.childCount),
});

// 정책대출 판정용 입력 검증 (모두 선택, 넣으면 범위 확인)
export function validatePolicyInputs(form) {
  const errors = {};
  if (form.housePrice !== "" && Number(form.housePrice) <= 0)
    errors.housePrice = "주택 가격은 0보다 커야 합니다.";
  if (
    form.householdAnnualIncome !== "" &&
    Number(form.householdAnnualIncome) <= 0
  )
    errors.householdAnnualIncome = "부부합산 연소득은 0보다 커야 합니다.";
  if (
    form.childCount !== "" &&
    (Number(form.childCount) < 0 || !Number.isInteger(Number(form.childCount)))
  )
    errors.childCount = "자녀 수는 0 이상의 정수여야 합니다.";
  return errors;
}

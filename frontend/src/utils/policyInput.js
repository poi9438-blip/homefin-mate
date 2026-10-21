// 정책대출 판정용 입력 (대출 화면·정책 화면 공통, 모두 필수)
export const POLICY_INPUT_DEFAULTS = {
  housePrice: "",
  householdAnnualIncome: "",
  firstHome: "",
  newlywed: "",
  childCount: "",
};

// '예/아니오' 선택값 → true / false (선택 전이면 undefined)
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

// 정책대출 판정용 입력 검증 (모두 필수: 비우면 조건을 판정할 수 없어 추천이 나오지 않음)
export function validatePolicyInputs(form) {
  const errors = {};
  if (form.housePrice === "") errors.housePrice = "주택 가격을 입력해주세요.";
  else if (Number(form.housePrice) <= 0)
    errors.housePrice = "주택 가격은 0보다 커야 합니다.";
  if (form.householdAnnualIncome === "")
    errors.householdAnnualIncome = "부부합산 연소득을 입력해주세요.";
  else if (Number(form.householdAnnualIncome) <= 0)
    errors.householdAnnualIncome = "부부합산 연소득은 0보다 커야 합니다.";
  if (form.firstHome === "") errors.firstHome = "생애최초 여부를 선택해주세요.";
  if (form.newlywed === "") errors.newlywed = "신혼가구 여부를 선택해주세요.";
  if (form.childCount === "") errors.childCount = "자녀 수를 입력해주세요.";
  else if (
    Number(form.childCount) < 0 ||
    !Number.isInteger(Number(form.childCount))
  )
    errors.childCount = "자녀 수는 0 이상의 정수여야 합니다.";
  return errors;
}

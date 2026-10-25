import { beforeEach, describe, expect, it } from "vitest";
import {
  getToken,
  getUser,
  isAuthenticated,
  removeToken,
  setToken,
  setUser,
} from "./auth";

// 로그인 정보 저장·삭제 테스트
describe("auth 저장소", () => {
  beforeEach(() => localStorage.clear());

  it("[TC-WEB-201] 로그인 정보 저장 후 로그아웃(removeToken)하면 토큰·사용자 정보 삭제", () => {
    setToken("jwt-token");
    setUser({ email: "user@test.com", role: "USER" });
    expect(isAuthenticated()).toBe(true);

    removeToken();

    expect(getToken()).toBeNull();
    expect(getUser()).toBeNull();
    expect(isAuthenticated()).toBe(false);
  });

  it("[TC-WEB-202] 저장된 사용자 정보가 깨진 JSON이면 오류 없이 null", () => {
    localStorage.setItem("hfm_user", "{broken");
    expect(getUser()).toBeNull();
  });

  // 로그아웃하면 입력 임시 저장(월 소득 등)도 함께 삭제, 다른 저장 값은 그대로
  it("[TC-WEB-203] 로그아웃하면 입력 임시 저장(월 소득 등)도 삭제 (#43 해결)", () => {
    setToken("jwt-token");
    localStorage.setItem(
      "hfm_draft_loan",
      JSON.stringify({
        monthlyIncome: "4000000",
        existingMonthlyInterest: "500000",
      }),
    );

    localStorage.setItem("hfm_theme", "dark");

    removeToken();

    expect(localStorage.getItem("hfm_draft_loan")).toBeNull();
    expect(localStorage.getItem("hfm_theme")).toBe("dark");
  });
});

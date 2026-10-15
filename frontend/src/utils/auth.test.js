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

  it("[TC-WEB-203] ⚠️#43 로그아웃해도 입력 임시 저장(월 소득 등)은 남음", () => {
    setToken("jwt-token");
    localStorage.setItem(
      "hfm_draft_loan",
      JSON.stringify({ monthlyIncome: "4000000", existingDebt: "500000" }),
    );

    removeToken();

    expect(
      JSON.parse(localStorage.getItem("hfm_draft_loan")).monthlyIncome,
    ).toBe("4000000");
  });
});

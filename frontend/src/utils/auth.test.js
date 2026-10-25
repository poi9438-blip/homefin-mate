import { beforeEach, describe, expect, it } from "vitest";
import { getToken, isAuthenticated, removeToken, setToken } from "./auth";

// 로그인 정보 저장·삭제 테스트
describe("auth 저장소", () => {
  beforeEach(() => localStorage.clear());

  it("[TC-WEB-201] 토큰 저장 후 로그아웃(removeToken)하면 토큰과 예전 버전의 사용자 정보(hfm_user) 삭제", () => {
    setToken("jwt-token");
    localStorage.setItem(
      "hfm_user",
      JSON.stringify({ email: "user@test.com", role: "USER" }),
    );
    expect(isAuthenticated()).toBe(true);

    removeToken();

    expect(getToken()).toBeNull();
    expect(localStorage.getItem("hfm_user")).toBeNull();
    expect(isAuthenticated()).toBe(false);
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

  // exp(초)를 넣은 테스트용 JWT
  const jwtWithExp = (exp) =>
    `x.${btoa(JSON.stringify({ sub: "user@test.com", exp })).replace(/=+$/, "")}.sig`;

  // 만료 토큰은 로그인 상태가 아니고 삭제됨, 남은 토큰·읽을 수 없는 토큰은 서버 판단에 맡김
  it("[TC-WEB-216] 만료 시각이 지난 토큰은 로그인 상태가 아니고 삭제됨, 만료 전·읽을 수 없는 토큰은 로그인 상태 (#77)", () => {
    const now = Math.floor(Date.now() / 1000);

    setToken(jwtWithExp(now - 60));
    expect(isAuthenticated()).toBe(false);
    expect(getToken()).toBeNull();

    setToken(jwtWithExp(now + 3600));
    expect(isAuthenticated()).toBe(true);

    setToken("not-a-jwt");
    expect(isAuthenticated()).toBe(true);
  });
});

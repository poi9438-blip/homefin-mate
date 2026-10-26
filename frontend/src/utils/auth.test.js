import { beforeEach, describe, expect, it } from "vitest";
import { clearSession, isAuthenticated, setSession } from "./auth";

// 로그인 상태(만료 시각) 저장·삭제 테스트 (토큰은 HttpOnly 쿠키라 화면에 없음, #78)
describe("auth 저장소", () => {
  beforeEach(() => localStorage.clear());

  it("[TC-WEB-201] 로그인(setSession) 후 로그아웃(clearSession)하면 로그인 상태가 아니고, 예전 버전의 hfm_token·hfm_user도 삭제", () => {
    localStorage.setItem("hfm_token", "old-jwt");
    localStorage.setItem(
      "hfm_user",
      JSON.stringify({ email: "user@test.com" }),
    );
    setSession(Date.now() + 3_600_000);
    expect(isAuthenticated()).toBe(true);
    expect(localStorage.getItem("hfm_token")).toBeNull();

    localStorage.setItem("hfm_user", "{}");
    clearSession();

    expect(isAuthenticated()).toBe(false);
    expect(localStorage.getItem("hfm_user")).toBeNull();
    expect(localStorage.getItem("hfm_session_expires_at")).toBeNull();
  });

  // 로그아웃하면 입력 임시 저장(월 소득 등)도 함께 삭제, 다른 저장 값은 그대로
  it("[TC-WEB-203] 로그아웃하면 입력 임시 저장(월 소득 등)도 삭제 (#43 해결)", () => {
    setSession(Date.now() + 3_600_000);
    localStorage.setItem(
      "hfm_draft_loan",
      JSON.stringify({
        monthlyIncome: "4000000",
        existingMonthlyInterest: "500000",
      }),
    );

    localStorage.setItem("hfm_theme", "dark");

    clearSession();

    expect(localStorage.getItem("hfm_draft_loan")).toBeNull();
    expect(localStorage.getItem("hfm_theme")).toBe("dark");
  });

  // 만료 시각이 지났으면 로그인 상태가 아니고 정리됨, 만료 전이면 로그인 상태, 로그인한 적 없으면 아님
  it("[TC-WEB-216] 만료 시각이 지나면 로그인 상태가 아니고 정리됨, 만료 전이면 로그인 상태, 로그인한 적 없으면 아님 (#77)", () => {
    expect(isAuthenticated()).toBe(false);

    setSession(Date.now() - 60_000);
    expect(isAuthenticated()).toBe(false);
    expect(localStorage.getItem("hfm_session_expires_at")).toBeNull();

    setSession(Date.now() + 3_600_000);
    expect(isAuthenticated()).toBe(true);
  });
});

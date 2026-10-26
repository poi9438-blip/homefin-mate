import { describe, expect, it } from "vitest";
import { errorMessage } from "./errorMessage";

describe("화면 오류 문구", () => {
  // 500·연결 실패는 화면 문구, 400은 서버의 검증 메시지
  it("[TC-WEB-209] 서버 오류(500)·연결 실패는 화면 문구, 입력 오류(400)는 서버 메시지를 보여 줌", () => {
    const fallback = "분석에 실패했습니다. 다시 시도해주세요.";

    expect(
      errorMessage(
        {
          response: { status: 500, data: { message: "Internal server error" } },
        },
        fallback,
      ),
    ).toBe(fallback);
    expect(errorMessage({ message: "Network Error" }, fallback)).toBe(fallback);
    expect(
      errorMessage(
        {
          response: {
            status: 400,
            data: { message: "termMonths: Term must be at least 1 month" },
          },
        },
        fallback,
      ),
    ).toBe("termMonths: Term must be at least 1 month");
    expect(
      errorMessage({ response: { status: 400, data: {} } }, fallback),
    ).toBe(fallback);
  });

  // 로그인 실패·시도 제한·중복 가입·401·403은 한국어, 그 밖의 4xx는 서버 메시지
  it("[TC-WEB-222] 로그인 실패·시도 제한·중복 가입 메시지와 401·403은 한국어로, 모르는 4xx 메시지는 그대로", () => {
    const err = (status, message) => ({
      response: { status, data: { message } },
    });
    expect(errorMessage(err(401, "Invalid credentials"), "x")).toBe(
      "이메일 또는 비밀번호가 올바르지 않습니다.",
    );
    expect(
      errorMessage(err(429, "Too many login attempts. Try again later"), "x"),
    ).toContain("15분 뒤");
    expect(errorMessage(err(400, "Email already registered"), "x")).toBe(
      "이미 가입된 이메일입니다.",
    );
    expect(errorMessage(err(403, "Access denied"), "x")).toBe(
      "이 기능을 사용할 권한이 없습니다.",
    );
    expect(errorMessage(err(401, "Unauthorized"), "x")).toBe(
      "로그인이 필요합니다.",
    );
    expect(errorMessage(err(400, "email: Email must be valid"), "x")).toBe(
      "email: Email must be valid",
    );
  });
});

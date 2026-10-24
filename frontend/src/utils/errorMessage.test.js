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
});

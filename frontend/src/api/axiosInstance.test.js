import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import axiosInstance from "./axiosInstance";

// 공통 axios 설정(인증 헤더, 401 처리) 테스트
describe("axiosInstance", () => {
  const originalAdapter = axiosInstance.defaults.adapter;
  const originalLocation = window.location;

  beforeEach(() => {
    localStorage.clear();
    document.cookie = "XSRF-TOKEN=; max-age=0";
    Object.defineProperty(window, "location", {
      value: { href: "/dashboard" },
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    axiosInstance.defaults.adapter = originalAdapter;
    Object.defineProperty(window, "location", {
      value: originalLocation,
      writable: true,
      configurable: true,
    });
  });

  // 지정한 상태 코드로 응답하는 가짜 adapter (2xx가 아니면 axios처럼 예외)
  const respondWith =
    (status, seen = {}) =>
    (config) => {
      seen.headers = config.headers;
      const response = {
        data: {},
        status,
        statusText: "",
        headers: {},
        config,
      };
      return status < 300
        ? Promise.resolve(response)
        : Promise.reject({ response, config });
    };

  it("[TC-WEB-204] 요청에 Authorization 헤더는 없고, XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로, X-Trace-Id도 붙임 (#78)", async () => {
    localStorage.setItem("hfm_token", "old-jwt");
    document.cookie = "XSRF-TOKEN=xsrf-123";
    const seen = {};
    axiosInstance.defaults.adapter = respondWith(200, seen);

    await axiosInstance.post("/loan/calculate", {});

    expect(seen.headers.Authorization).toBeUndefined();
    expect(seen.headers["X-XSRF-TOKEN"]).toBe("xsrf-123");
    expect(seen.headers["X-Trace-Id"]).toBeTruthy();
  });

  it("[TC-WEB-205] 401 응답이면 로그인 상태를 지우고 /login으로 이동", async () => {
    localStorage.setItem(
      "hfm_session_expires_at",
      String(Date.now() + 3600000),
    );
    axiosInstance.defaults.adapter = respondWith(401);

    await expect(axiosInstance.get("/loan/history")).rejects.toBeDefined();

    expect(localStorage.getItem("hfm_session_expires_at")).toBeNull();
    expect(window.location.href).toBe("/login");
  });

  // 로그인 실패(401)는 토큰 만료가 아니라서 토큰·화면을 그대로 두고 오류만 넘김
  it("[TC-WEB-210] 로그인 요청의 401은 로그인 상태를 지우지 않고 이동하지 않음 (화면이 오류 문구 표시)", async () => {
    localStorage.setItem("hfm_session_expires_at", "other-session");
    axiosInstance.defaults.adapter = respondWith(401);

    const error = await axiosInstance.post("/auth/login", {}).catch((e) => e);

    expect(error.response.status).toBe(401);
    expect(localStorage.getItem("hfm_session_expires_at")).toBe(
      "other-session",
    );
    expect(window.location.href).toBe("/dashboard");
  });

  it("[TC-WEB-206] 500 응답이면 로그인 상태를 유지하고 이동하지 않음", async () => {
    localStorage.setItem("hfm_session_expires_at", "kept");
    axiosInstance.defaults.adapter = respondWith(500);

    await expect(axiosInstance.get("/loan/history")).rejects.toBeDefined();

    expect(localStorage.getItem("hfm_session_expires_at")).toBe("kept");
    expect(window.location.href).toBe("/dashboard");
  });

  // 본문에 traceId가 있으면 헤더도 같은 값, 없으면 새로 만든 hfm- 값
  it("[TC-WEB-217] 본문에 traceId가 있으면 X-Trace-Id 헤더도 같은 값, 없으면 새 hfm- 값 (#3)", async () => {
    const seen = {};
    axiosInstance.defaults.adapter = respondWith(200, seen);

    await axiosInstance.post("/loan/calculate", {
      loanAmount: 1,
      traceId: "hfm-body-trace",
    });
    expect(seen.headers["X-Trace-Id"]).toBe("hfm-body-trace");

    await axiosInstance.get("/loan/history");
    expect(seen.headers["X-Trace-Id"]).toMatch(/^hfm-/);
    expect(seen.headers["X-Trace-Id"]).not.toBe("hfm-body-trace");
  });
});

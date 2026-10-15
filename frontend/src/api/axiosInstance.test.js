import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import axiosInstance from "./axiosInstance";

// 공통 axios 설정(인증 헤더, 401 처리) 테스트
describe("axiosInstance", () => {
  const originalAdapter = axiosInstance.defaults.adapter;
  const originalLocation = window.location;

  beforeEach(() => {
    localStorage.clear();
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

  it("[TC-WEB-204] 토큰이 있으면 Authorization: Bearer와 X-Trace-Id 헤더를 붙임", async () => {
    localStorage.setItem("hfm_token", "jwt-token");
    const seen = {};
    axiosInstance.defaults.adapter = respondWith(200, seen);

    await axiosInstance.get("/loan/history");

    expect(seen.headers.Authorization).toBe("Bearer jwt-token");
    expect(seen.headers["X-Trace-Id"]).toBeTruthy();
  });

  it("[TC-WEB-205] 401 응답이면 토큰을 지우고 /login으로 이동", async () => {
    localStorage.setItem("hfm_token", "expired-token");
    axiosInstance.defaults.adapter = respondWith(401);

    await expect(axiosInstance.get("/loan/history")).rejects.toBeDefined();

    expect(localStorage.getItem("hfm_token")).toBeNull();
    expect(window.location.href).toBe("/login");
  });

  it("[TC-WEB-206] 500 응답이면 토큰을 유지하고 이동하지 않음", async () => {
    localStorage.setItem("hfm_token", "jwt-token");
    axiosInstance.defaults.adapter = respondWith(500);

    await expect(axiosInstance.get("/loan/history")).rejects.toBeDefined();

    expect(localStorage.getItem("hfm_token")).toBe("jwt-token");
    expect(window.location.href).toBe("/dashboard");
  });
});

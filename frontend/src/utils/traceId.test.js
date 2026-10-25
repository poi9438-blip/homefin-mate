import { describe, expect, it } from "vitest";
import { generateTraceId } from "./traceId";

describe("traceId", () => {
  // traceId는 hfm- + UUID v4 형식, 1000번 만들어도 겹치지 않음
  it("[TC-WEB-219] traceId는 hfm- + UUID v4 형식이고 1000번 만들어도 겹치지 않음 (#86)", () => {
    const ids = Array.from({ length: 1000 }, () => generateTraceId());

    ids.forEach((id) =>
      expect(id).toMatch(
        /^hfm-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
      ),
    );
    expect(new Set(ids).size).toBe(1000);
  });
});

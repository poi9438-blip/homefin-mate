// UUID v4 만들기 (임의 16바이트, 버전·variant 비트 설정)
function uuidV4() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

// 요청 추적용 traceId 생성 (hfm- + UUID v4)
export function generateTraceId() {
  return `hfm-${uuidV4()}`;
}

// 서비스 간 요청 형식 버전
export const DEFAULT_EVENT_VERSION = "v1";

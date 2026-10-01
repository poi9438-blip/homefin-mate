import { v4 as uuidv4 } from "uuid";

// 요청 추적용 traceId 생성
export function generateTraceId() {
  return `hfm-${uuidv4()}`;
}

// 서비스 간 요청 형식 버전
export const DEFAULT_EVENT_VERSION = "v1";

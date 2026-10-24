// 화면에 보여 줄 오류 문구 (5xx·연결 실패는 화면 문구, 4xx는 서버 메시지)
export function errorMessage(err, fallback) {
  const status = err?.response?.status;
  if (!status || status >= 500) return fallback;
  return err.response.data?.message || fallback;
}

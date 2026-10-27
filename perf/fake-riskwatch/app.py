"""
성능 측정용 가짜 RiskWatch.

실제 RiskWatch 대신 응답 지연·실패를 마음대로 만들어 외부 시스템 장애 상황을 재현한다.
- DELAY_SECONDS: 응답 전에 기다리는 시간 (기본 60초 = 응답하지 않는 장애)
- MODE: ok (지연 후 성공 응답) | error (지연 후 503) | html502 (지연 후 502 + 긴 HTML 오류 페이지, 앞단 프록시 장애 흉내)
"""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DELAY_SECONDS = float(os.environ.get("DELAY_SECONDS", "60"))
MODE = os.environ.get("MODE", "ok")

# 받은 평가 요청 수 (서킷 브레이커가 호출을 얼마나 줄였는지 세기 위함)
# GET /stats → {"requests": N}, DELETE /stats → 0으로 초기화
_count = 0
_lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/stats":
            self._send_json(200, {"requests": _count})
        else:
            self._send_json(404, {})

    def do_DELETE(self):
        global _count
        if self.path == "/stats":
            with _lock:
                _count = 0
            self._send_json(200, {"requests": 0})
        else:
            self._send_json(404, {})

    def _send_json(self, status, obj):
        body = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        global _count
        with _lock:
            _count += 1
        length = int(self.headers.get("Content-Length", 0))
        self.rfile.read(length)
        time.sleep(DELAY_SECONDS)

        if MODE == "error":
            self.send_response(503)
            self.end_headers()
            return

        if MODE == "html502":
            html = ("<html><head><title>502 Bad Gateway</title></head><body>"
                    "<center><h1>502 Bad Gateway</h1></center>"
                    "<p>" + "upstream riskwatch connect() failed. " * 10 + "</p>"
                    "<hr><center>nginx</center></body></html>").encode()
            self.send_response(502)
            self.send_header("Content-Type", "text/html")
            self.send_header("Content-Length", str(len(html)))
            self.end_headers()
            self.wfile.write(html)
            return

        # RiskWatch 평가 API(POST /api/evaluations)와 같은 모양의 판정 응답 (토큰은 확인하지 않음)
        body = json.dumps({
            "status": "SUCCESS",
            "data": {"evaluationId": "00000000-0000-0000-0000-000000000000", "productCode": "BOGEUMJARI",
                     "verdict": "ELIGIBLE", "riskLevel": "LOW", "riskScore": 0, "recommendation": "fake riskwatch",
                     "criteriaVersion": "fake", "reasons": [], "notEvaluated": []},
        }).encode()
        self.send_response(201)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"fake riskwatch: delay={DELAY_SECONDS}s mode={MODE}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8120), Handler).serve_forever()

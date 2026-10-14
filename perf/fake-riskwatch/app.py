"""
성능 측정용 가짜 RiskWatch.

실제 RiskWatch 대신 응답 지연·실패를 마음대로 만들어 외부 시스템 장애 상황을 재현한다.
- DELAY_SECONDS: 응답 전에 기다리는 시간 (기본 60초 = 응답하지 않는 장애)
- MODE: ok (지연 후 성공 응답) | error (지연 후 503) | html502 (지연 후 502 + 긴 HTML 오류 페이지, 앞단 프록시 장애 흉내)
"""
import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DELAY_SECONDS = float(os.environ.get("DELAY_SECONDS", "60"))
MODE = os.environ.get("MODE", "ok")


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
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

        body = json.dumps({
            "status": "SUCCESS",
            "data": {"riskLevel": "LOW", "riskScore": 10, "reason": "fake riskwatch", "traceId": ""},
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"fake riskwatch: delay={DELAY_SECONDS}s mode={MODE}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8120), Handler).serve_forever()

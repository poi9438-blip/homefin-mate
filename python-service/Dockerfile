# Python 3.11 실행 환경
FROM python:3.11-slim
WORKDIR /app

# 의존성 설치 (캐시 활용)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 실행 전용 사용자 (root 아님)
RUN useradd --system --no-create-home app

# 서비스 코드 복사
COPY --chown=app:app . .
USER app
EXPOSE 8200

# uvicorn으로 8200 포트 실행
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8200"]

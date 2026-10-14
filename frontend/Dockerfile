# 1단계: Vite로 정적 파일 빌드
FROM node:20-alpine AS build
WORKDIR /app

# 의존성 설치 (캐시 활용)
COPY package*.json ./
RUN npm install

# 소스 복사 후 빌드 (결과: /app/dist)
COPY . .
RUN npm run build

# 2단계: nginx로 정적 파일 서비스 + /api 프록시
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
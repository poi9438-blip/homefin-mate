# 1단계: Maven으로 jar 빌드
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 의존성 먼저 받기 (캐시 활용)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 소스 복사 후 jar 빌드 (테스트 생략)
COPY src ./src
RUN mvn package -DskipTests -q

# 2단계: JRE로 jar 실행 (기본 이미지, compose 기본 target)
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
# 실행 전용 사용자 (root 아님)
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "app.jar"]

# 3단계: OTel Java 에이전트 포함 이미지 (observability/docker-compose.observability.yml)
FROM runtime AS otel
ADD --chown=app:app --checksum=sha256:5f9d1f10056ed2b246fbb19543519b8066cda23d6aff19022dbdb84402d6c3ab     https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.33.0/opentelemetry-javaagent-2.33.0.jar     /app/opentelemetry-javaagent.jar
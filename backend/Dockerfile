# 1단계: Maven으로 jar 빌드
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 의존성 먼저 받기 (캐시 활용)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 소스 복사 후 jar 빌드 (테스트 생략)
COPY src ./src
RUN mvn package -DskipTests -q

# 2단계: JRE로 jar 실행
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 실행 전용 사용자 (root 아님)
RUN addgroup -S app && adduser -S app -G app
COPY --from=build --chown=app:app /app/target/*.jar app.jar
USER app
EXPOSE 8100
ENTRYPOINT ["java", "-jar", "app.jar"]
# syntax=docker/dockerfile:1
# 백엔드 이미지. 테스트는 CI 에서 따로 돌리므로 여기서는 패키징만 한다.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
# 의존성 레이어 캐시: pom 이 안 바뀌면 다시 받지 않는다
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline -Djacoco.skip=true
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Djacoco.skip=true

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/target/hiretrack.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080 9090
# 운영 프로필은 Actuator 를 9090 (외부 비공개)에 띄운다
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=4 \
    CMD wget -qO- http://localhost:9090/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

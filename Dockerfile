# 빌드 (Gradle)
FROM gradle:9.5.1-jdk21-alpine AS build
WORKDIR /home/gradle/project

# 의존성만 먼저 복사해 레이어 캐시를 최대한 활용 (소스 변경 시 재다운로드 방지)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon --build-cache || true

COPY src ./src

RUN ./gradlew build --no-daemon --build-cache -x test

# 실행 (JRE)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata \
    && addgroup -S spring && adduser -S spring -G spring

ENV TZ=Asia/Seoul
ENV JAVA_HEAP_PERCENT=75
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Duser.timezone=Asia/Seoul"

COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN sed -i 's/\r$//' /docker-entrypoint.sh && chmod +x /docker-entrypoint.sh

USER spring:spring

EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]

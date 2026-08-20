FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Najpierw sam wrapper i pliki builda: warstwa z zaleznosciami przetrwa zmiane kodu
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY shared/build.gradle.kts ./shared/
COPY identity/build.gradle.kts ./identity/
COPY workspace/build.gradle.kts ./workspace/
COPY tasks/build.gradle.kts ./tasks/
COPY automation/build.gradle.kts ./automation/
COPY analytics/build.gradle.kts ./analytics/
COPY app/build.gradle.kts ./app/
RUN ./gradlew --no-daemon :app:dependencies > /dev/null 2>&1 || true

COPY shared/src ./shared/src
COPY identity/src ./identity/src
COPY workspace/src ./workspace/src
COPY tasks/src ./tasks/src
COPY automation/src ./automation/src
COPY analytics/src ./analytics/src
COPY app/src ./app/src
RUN ./gradlew --no-daemon :app:bootJar -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd --system --uid 10001 nowtask
COPY --from=build /app/app/build/libs/nowtask.jar app.jar
USER nowtask

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -qO- http://localhost:8081/actuator/health/readiness > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

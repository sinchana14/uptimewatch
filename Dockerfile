# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with Maven ----
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
# Download dependencies first so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI before the image is built, so skip them here to keep builds fast.
RUN mvn -B -q package -DskipTests && cp target/uptimewatch-*.jar /src/app.jar

# ---- Runtime stage: small JRE image, non-root user ----
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system app && useradd --system --gid app --home /app app \
    && apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/app.jar app.jar
RUN mkdir -p /app/data && chown -R app:app /app
USER app

# 8080 = status page and API, 8081 = health and Prometheus metrics (keep private)
EXPOSE 8080 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fs http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

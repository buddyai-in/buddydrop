# syntax=docker/dockerfile:1

# ---- build stage: compile the boot jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Resolve dependencies first so they cache across source-only changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests run in CI; skip here to keep image builds fast.
RUN mvn -q -B clean package -DskipTests

# ---- runtime stage: slim JRE, non-root ----
# BellSoft Liberica JRE on Alpine: slim + musl, and multi-arch (linux/amd64 and linux/arm64),
# so the image builds on x86 and on arm64 hosts/Graviton alike. (Temurin's Alpine images are
# amd64-only, which breaks arm64 builds with "no match for platform in manifest".)
FROM bellsoft/liberica-openjre-alpine:17 AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENV JAVA_OPTS=""
# Uses the actuator health endpoint exposed by the app.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

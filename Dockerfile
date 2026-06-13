# Dockerfile
# Digital Bank API — multi-stage build (Spring Boot 3.4 / Java 17 / Maven)

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Resolve dependencies first to leverage Docker layer cache (only re-runs when pom.xml changes)
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Build the application (tests skipped — run them in CI, not in the image build)
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

# Non-root user (least privilege)
RUN groupadd --system --gid 10001 appgroup \
 && useradd  --system --uid 10001 --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

# finalName=digital-bank => target/digital-bank.jar
COPY --from=build --chown=appuser:appgroup /app/target/digital-bank.jar /app/app.jar

USER appuser:appgroup

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]

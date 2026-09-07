# Stage 1: Build 
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom first (layer cache optimization)
COPY pom.xml ./
RUN mvn dependency:go-offline -B -q

# Copy source and build (skip tests — tests run in CI pipeline)
COPY src src
RUN mvn package -DskipTests -B -q

# Stage 2: Runtime 
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

# Copy the fat JAR
COPY --chown=appuser:appgroup --from=builder /build/target/*.jar app.jar

USER appuser

# JVM tuning for containers
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

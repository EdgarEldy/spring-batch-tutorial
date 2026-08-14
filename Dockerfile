# Build stage: compiles the jar with Maven and the exact JDK the project targets.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy the pom first so Maven's dependency cache layer only invalidates when
# dependencies change, not on every source edit.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Runtime stage: a JRE-only image, no Maven or build tools shipped to production.
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN useradd --system --create-home appuser \
    && mkdir -p /app/logs \
    && chown -R appuser:appuser /app
USER appuser

COPY --from=build --chown=appuser:appuser /workspace/target/spring-batch-tutorial-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

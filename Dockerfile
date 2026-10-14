# syntax=docker/dockerfile:1
# Build: official Maven image (no wrapper needed). Run: JRE only, non-root, layered for small rebuilds.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml dependency-check-suppressions.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && cp target/identity-service-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER app
# Add the instance when running it (ADR-0009): SPRING_PROFILES_ACTIVE=prod,customer or prod,staff.
# With neither, Identity refuses to start.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
# 9000: Customer Identity, 9001: Staff Identity, 8081: actuator
EXPOSE 9000 9001 8081
ENTRYPOINT ["java", "-jar", "application.jar"]

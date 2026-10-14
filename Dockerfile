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

FROM eclipse-temurin:24-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 9000 8081
ENTRYPOINT ["java", "-jar", "application.jar"]

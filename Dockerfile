# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q verify

FROM eclipse-temurin:21-jre-jammy AS production
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 janus \
    && useradd --uid 10001 --gid janus --no-create-home --shell /usr/sbin/nologin janus
COPY --from=build --chown=janus:janus /app/target/janussync.jar ./janussync.jar
ENV TZ=America/Bogota
USER janus
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-Duser.timezone=America/Bogota", "-jar", "/app/janussync.jar"]

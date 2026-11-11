# syntax=docker/dockerfile:1

# Multi-stage build for the homelabs app (backend for frontend + shell).
# BUILD CONTEXT = this repository's root:
#   docker build -t homelabs-app .
#
# Single Maven module, no frontend build chain: the shell's ES modules, styles
# and icons ship as static resources inside the jar. Tests run on the host or
# in CI via `mvn verify` (they need Docker for the Postgres test container),
# never inside the image build.

# ---- Stage 1: build the jar (JDK 25) ----
FROM maven:3.10.0-eclipse-temurin-25-noble AS build
WORKDIR /build

COPY pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp package -DskipTests

# ---- Stage 2: runtime (JRE 25, glibc-based) ----
FROM eclipse-temurin:25-jre-noble

# Non-root: the noble base image ships the `ubuntu` user (uid/gid 1000).
WORKDIR /opt/app

COPY --from=build --chown=ubuntu:ubuntu \
     /build/target/homelabs-app-0.0.1-SNAPSHOT.jar app.jar

USER ubuntu
EXPOSE 8095

# Issuer, client secret, session database and the micro frontend registrations
# come from the environment (see src/main/resources/application.yml).
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]

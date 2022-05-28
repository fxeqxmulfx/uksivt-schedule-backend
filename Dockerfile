FROM gradle:7.4.2-jdk17 as builder

WORKDIR /temp

COPY src src
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

RUN gradle shadowJar --no-daemon

FROM openjdk:17.0.2-slim

WORKDIR backend

COPY --from=builder /temp/build/libs/*.jar backend.jar

ENV pg_host=CHANGEME
ENV pg_database=CHANGEME
ENV pg_user=CHANGEME
ENV pg_password=CHANGEME

ENTRYPOINT java -jar backend.jar
EXPOSE 8080
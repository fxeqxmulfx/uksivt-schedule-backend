FROM gradle:7.5-jdk17 as builder

WORKDIR /temp

COPY src src
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

RUN gradle shadowJar --no-daemon

FROM openjdk:17.0.2-slim

WORKDIR backend

COPY --from=builder /temp/build/libs/*all.jar backend.jar

ENV PG_HOST=CHANGEME
ENV PG_DB=CHANGEME
ENV PG_USER=CHANGEME
ENV PG_PASSWORD=CHANGEME
ENV GOOGLE_API=CHANGEME

LABEL traefik.enable=true
LABEL traefik.http.routers.uksivt_shedule_back.entrypoints=websecure
LABEL traefik.http.routers.uksivt_shedule_back.rule=Host(`back.uksivt.com`)
LABEL traefik.http.routers.uksivt_shedule_back.tls=true
LABEL traefik.http.routers.uksivt_shedule_back.tls.certresolver=production

ENTRYPOINT ["java", "-jar", "backend.jar"]
EXPOSE 8080
# syntax=docker/dockerfile:1.4

FROM gradle:8.10-jdk21 AS build
WORKDIR /app

ENV GRADLE_USER_HOME=/home/gradle/.gradle

COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# Dependencies layer: reused until Gradle build files change
RUN --mount=type=cache,target=/home/gradle/.gradle,sharing=locked \
    ./gradlew dependencies --no-daemon -q

COPY src/ src/

RUN --mount=type=cache,target=/home/gradle/.gradle,sharing=locked \
    ./gradlew installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/osu-mps/ /app/
EXPOSE 8080
CMD ["/app/bin/osu-mps"]

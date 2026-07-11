# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :cloudislands-core-service:installDist

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/cloudislands
COPY --from=build /workspace/cloudislands-core-service/build/install/cloudislands-core-service/ ./
EXPOSE 8443
ENTRYPOINT ["bin/cloudislands-core-service"]

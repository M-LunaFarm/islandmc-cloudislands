# syntax=docker/dockerfile:1.7
ARG JAVA_IMAGE=eclipse-temurin:21-jdk-jammy
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy
FROM ${JAVA_IMAGE} AS build

ARG VELOCITY_VERSION=3.5.0-SNAPSHOT
WORKDIR /workspace
RUN apt-get update \
    && apt-get install --yes --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :cloudislands-velocity:shadowJar
RUN python3 scripts/ci/download_papermc.py \
    --project velocity \
    --version "${VELOCITY_VERSION}" \
    --output /workspace/velocity-server.jar

FROM ${JAVA_RUNTIME_IMAGE}

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /data
COPY --from=build /workspace/velocity-server.jar /opt/cloudislands/server.jar
COPY --from=build /workspace/cloudislands-velocity/build/libs/CloudIslands-Velocity-*.jar /opt/cloudislands/CloudIslands-Velocity.jar
COPY deploy/docker/velocity-entrypoint.sh /opt/cloudislands/entrypoint.sh
RUN chmod 0755 /opt/cloudislands/entrypoint.sh
EXPOSE 25565 8788
ENTRYPOINT ["/opt/cloudislands/entrypoint.sh"]

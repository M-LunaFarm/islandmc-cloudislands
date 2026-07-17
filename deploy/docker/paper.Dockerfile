# syntax=docker/dockerfile:1.7
ARG JAVA_IMAGE=eclipse-temurin:21-jdk-jammy
ARG PAPER_JAVA_IMAGE=eclipse-temurin:25-jdk-jammy
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:25-jre-jammy
FROM ${JAVA_IMAGE} AS build

WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :cloudislands-paper:shadowJar

FROM ${PAPER_JAVA_IMAGE} AS paper-runtime

ARG PAPER_VERSION=26.1.2
WORKDIR /workspace
RUN apt-get update \
    && apt-get install --yes --no-install-recommends python3 \
    && rm -rf /var/lib/apt/lists/*
COPY scripts/ci/ scripts/ci/
RUN python3 scripts/ci/download_papermc.py \
    --project paper \
    --version "${PAPER_VERSION}" \
    --output /workspace/paper-server.jar
RUN mkdir -p /workspace/paper-runtime \
    && cp /workspace/paper-server.jar /workspace/paper-runtime/server.jar \
    && cd /workspace/paper-runtime \
    && java -Dpaperclip.patchonly=true -jar server.jar

FROM ${JAVA_RUNTIME_IMAGE}

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /data
COPY --from=paper-runtime /workspace/paper-server.jar /opt/cloudislands/server.jar
COPY --from=paper-runtime /workspace/paper-runtime/cache/ /opt/cloudislands/paper-runtime/cache/
COPY --from=paper-runtime /workspace/paper-runtime/versions/ /opt/cloudislands/paper-runtime/versions/
COPY --from=build /workspace/cloudislands-paper/build/libs/CloudIslands-Paper-*.jar /opt/cloudislands/CloudIslands-Paper.jar
COPY deploy/docker/paper-entrypoint.sh /opt/cloudislands/entrypoint.sh
RUN chmod 0755 /opt/cloudislands/entrypoint.sh
EXPOSE 25565 8789
ENTRYPOINT ["/opt/cloudislands/entrypoint.sh"]

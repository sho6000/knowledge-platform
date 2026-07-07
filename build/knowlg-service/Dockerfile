# # build stage
# FROM maven:3.9-eclipse-temurin-11 AS build
# WORKDIR /build
# COPY . .
# RUN mvn clean install -DskipTests=true && mvn play2:dist -pl knowlg-service

# # FROM --platform=linux/x86_64 eclipse-temurin:11.0.20.1_1-jdk-focal
# FROM --platform=linux/x86_64 ${DHI_IMAGE_DEV} AS prep
# RUN apt-get update \
#     && apt-get upgrade -y \
#     && apt-get install -y unzip curl \
#     && adduser --uid 1001 --home /home/sunbird/ --disabled-login sunbird \
#     && mkdir -p /home/sunbird \
#     && chown -R sunbird:sunbird /home/sunbird \
#     && apt-get clean \
#     && rm -rf /var/lib/apt/lists/*

# # # CSP selection: pass --build-arg CSP=<azure|aws|gcp|oci> at image build time (default: azure).
# # # The Maven build must have been run with the matching profile (-Pazure / -Paws / -Pgcp / -Poci)
# # # so that exactly one CSP runtime jar is present in lib/; Java ServiceLoader picks it up automatically.
# # # cloud_storage_auth_type defaults to OIDC (Kubernetes Workload Identity).
# # # Override via K8s ConfigMap or `docker run -e` for local ACCESS_KEY-based development.
# ARG CSP=azure
# ENV cloud_storage_type=${CSP}
# ENV cloud_storage_auth_type=OIDC

# USER sunbird
# # COPY ./knowlg-service/target/knowlg-service-1.0-SNAPSHOT-dist.zip /home/sunbird/
# COPY --from=build /build/knowlg-service/target/knowlg-service-1.0-SNAPSHOT-dist.zip /home/sunbird/
# RUN unzip /home/sunbird/knowlg-service-1.0-SNAPSHOT-dist.zip -d /home/sunbird/
# RUN rm /home/sunbird/knowlg-service-1.0-SNAPSHOT-dist.zip
# # COPY --chown=sunbird ./schemas /home/sunbird/knowlg-service-1.0-SNAPSHOT/schemas
# COPY --chown=sunbird --from=build /build/schemas /home/sunbird/knowlg-service-1.0-SNAPSHOT/schemas
# WORKDIR /home/sunbird/
# CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS -cp '/home/sunbird/knowlg-service-1.0-SNAPSHOT/lib/*' -Dconfig.file=/home/sunbird/knowlg-service-1.0-SNAPSHOT/config/application.conf -Dlogger.file=/home/sunbird/knowlg-service-1.0-SNAPSHOT/config/logback.xml play.core.server.ProdServerStart /home/sunbird/knowlg-service-1.0-SNAPSHOT
# -------

ARG DHI_IMAGE_DEV=dhi.io/eclipse-temurin:11.0.31.11-jdk-debian13-dev
ARG DHI_IMAGE_RUNTIME=dhi.io/eclipse-temurin:11.0.31-debian13

FROM dhi.io/busybox:1.38.0-alpine3.24 as shell

FROM --platform=linux/x86_64 ${DHI_IMAGE_DEV} AS build
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y unzip curl \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /home/sunbird
COPY ./knowlg-service/target/knowlg-service-1.0-SNAPSHOT-dist.zip .
RUN unzip knowlg-service-1.0-SNAPSHOT-dist.zip -d . && rm knowlg-service-1.0-SNAPSHOT-dist.zip
COPY ./schemas /home/sunbird/knowlg-service-1.0-SNAPSHOT/schemas

# ---- final runtime stage: minimal, nonroot, no shell ----
FROM --platform=linux/x86_64 ${DHI_IMAGE_RUNTIME}
COPY --from=shell /bin/busybox /bin/sh

ARG CSP=azure
ENV cloud_storage_type=${CSP}
ENV cloud_storage_auth_type=OIDC

USER nonroot
COPY --from=build --chown=nonroot:nonroot /home/sunbird /home/sunbird
WORKDIR /home/sunbird/

CMD java -XX:+PrintFlagsFinal $JAVA_OPTIONS -cp '/home/sunbird/knowlg-service-1.0-SNAPSHOT/lib/*' -Dconfig.file=/home/sunbird/knowlg-service-1.0-SNAPSHOT/config/application.conf -Dlogger.file=/home/sunbird/knowlg-service-1.0-SNAPSHOT/config/logback.xml play.core.server.ProdServerStart /home/sunbird/knowlg-service-1.0-SNAPSHOT






# run cmd
# docker build -f build/knowlg-service/Dockerfile -t knowlg-service:dhi .

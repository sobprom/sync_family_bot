FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17 AS build
USER root

RUN microdnf install -y maven && microdnf clean all

COPY --chown=quarkus:quarkus . /code/
USER quarkus
WORKDIR /code

RUN mvn package -Pnative -DskipTests \
    -Djooq.codegen.skip=true \
    -Dquarkus.generate-code.skip=true \
    -Dquarkus.native.native-image-xmx=5g \
    -Dquarkus.native.additional-buildargs="--allow-incomplete-classpath,--no-fallback"

FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=build /code/target/*-runner /work/application
RUN chmod 775 /work/application
EXPOSE 8080
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]

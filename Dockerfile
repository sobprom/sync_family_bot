# Этап 1: Сборка
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17 AS build
COPY --chown=quarkus:quarkus . /code/
USER quarkus
WORKDIR /code

RUN /usr/bin/mvn package -Pnative -DskipTests \
    -Dquarkus.native.native-image-xmx=5g \
    -Dquarkus.native.additional-buildargs="--allow-incomplete-classpath"

# Этап 2: Финальный образ
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=build /code/target/*-runner /work/application
RUN chmod 775 /work/application
EXPOSE 8080
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]

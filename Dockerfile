# Этап 1: Сборка нативного бинаря
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17 AS build
COPY --chown=quarkus:quarkus . /code/
USER quarkus
WORKDIR /code

# Ограничиваем память до 5ГБ, чтобы влезть в GitHub Runner (7ГБ)
RUN ${MAVEN_HOME}/bin/mvn package -Pnative -DskipTests \
    -Dquarkus.native.native-image-xmx=5g \
    -Dquarkus.native.additional-buildargs="--allow-incomplete-classpath"

# Этап 2: Финальный минимальный образ
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=build /code/target/*-runner /work/application
RUN chmod 775 /work/application
EXPOSE 8080
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]

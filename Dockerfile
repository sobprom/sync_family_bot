# Этап 1: Сборка нативного образа с Mandrel
FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17 AS build
COPY --chown=quarkus:quarkus . /code/
USER quarkus
WORKDIR /code
# Сборка нативного бинаря (требует много RAM на этапе сборки!)
RUN ./mvnw package -Pnative -DskipTests

# Этап 2: Финальный минимальный образ (UBI Micro)
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=build /code/target/*-runner /work/application
RUN chmod 775 /work/application
EXPOSE 8080
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]

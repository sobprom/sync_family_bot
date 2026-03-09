FROM quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-17 AS mandrel

FROM maven:3.9.6-eclipse-temurin-17 AS build

# Копируем GraalVM из донора в рабочую среду
COPY --from=mandrel /usr/lib/graalvm /usr/lib/graalvm
ENV GRAALVM_HOME=/usr/lib/graalvm
ENV JAVA_HOME=/usr/lib/graalvm

# Копируем проект (включая target/ с jOOQ из Workflow)
COPY . /code
WORKDIR /code

# Запускаем сборку. Здесь 'mvn' ЕСТЬ, а 'skip' помогут не лезть в базу
RUN mvn package -Pnative -DskipTests \
    -Djooq.codegen.skip=true \
    -Dquarkus.generate-code.skip=true \
    -Dquarkus.native.native-image-xmx=5g \
    -Dquarkus.native.additional-buildargs="--allow-incomplete-classpath,--no-fallback"

# Финальный образ (максимально легкий)
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=build /code/target/*-runner /work/application
RUN chmod 775 /work/application
EXPOSE 8080
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]

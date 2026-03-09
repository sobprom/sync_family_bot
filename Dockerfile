# Используем образ, совместимый с тем, в котором собирали (UBI Micro)
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
# Файл *-runner уже лежит в target после шага в Workflow
COPY target/*-runner /work/application
RUN chmod 775 /work/application
EXPOSE 8080
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]

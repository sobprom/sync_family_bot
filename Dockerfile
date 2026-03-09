FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY target/*-runner /work/application
RUN chmod 775 /work/application
EXPOSE 8080
ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]

FROM eclipse-temurin:17-jre-alpine

WORKDIR /deployments

# В режиме uber-jar Quarkus создает файл с суффиксом -runner.jar в корне target/
COPY target/*-runner.jar /deployments/app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Dfile.encoding=UTF-8", \
    "--add-opens=java.base/java.lang=ALL-UNNAMED", \
    "--add-opens=java.base/java.util=ALL-UNNAMED", \
    "--add-opens=java.base/java.nio=ALL-UNNAMED", \
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/deployments/app.jar"]

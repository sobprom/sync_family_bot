FROM eclipse-temurin:17-jre-alpine

# Рабочая директория для приложения
WORKDIR /deployments

# Копируем структуру Quarkus fast-jar
# Предполагается, что 'mvn package' уже запущен в CI
COPY target/quarkus-app/lib/ /deployments/lib/
COPY target/quarkus-app/*.jar /deployments/app.jar
COPY target/quarkus-app/app/ /deployments/app/
COPY target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080

# Твои специфические флаги JVM
ENTRYPOINT ["java", \
    "-Dfile.encoding=UTF-8", \
    "--add-opens=java.base/java.lang=ALL-UNNAMED", \
    "--add-opens=java.base/java.util=ALL-UNNAMED", \
    "--add-opens=java.base/java.nio=ALL-UNNAMED", \
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/deployments/app.jar"]

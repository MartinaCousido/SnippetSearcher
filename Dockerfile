# Etapa 1: compila el servicio con el wrapper de Gradle.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Primero solo los archivos de build, para cachear la descarga de dependencias.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null

COPY src src
COPY newrelic newrelic
RUN ./gradlew bootJar copyNewRelic --no-daemon

# Etapa 2: imagen final, solo con el runtime de Java y el jar.
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --no-create-home app
COPY --from=build /app/build/libs/*.jar app.jar
# Agente de New Relic (#81). Sin NEW_RELIC_LICENSE_KEY, o con
# NEW_RELIC_AGENT_ENABLED=false, el servicio anda igual pero no reporta.
COPY --from=build /app/build/newrelic/newrelic.jar /app/newrelic.jar
COPY --from=build /app/build/newrelic/newrelic.yml /app/newrelic.yml
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/newrelic.jar", "-jar", "app.jar"]

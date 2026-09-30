# Etapa 1: compila el servicio con el wrapper de Gradle.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Primero solo los archivos de build, para cachear la descarga de dependencias.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon

# Etapa 2: imagen final, solo con el runtime de Java y el jar.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home app
COPY --from=build /app/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

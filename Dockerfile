# syntax=docker/dockerfile:1
FROM openjdk:25-ea-slim AS builder

RUN apt-get update && apt-get install -y maven curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml .
COPY src ./src

# Monta la carpeta ~/.m2/repository como una cache persistente de Docker
RUN --mount=type=cache,target=/root/.m2 mvn package -DskipTests

# -------------------------------------------------------------
# Etapa 2: Imagen de ejecución (Runtime)
# -------------------------------------------------------------
FROM openjdk:25-ea-slim

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
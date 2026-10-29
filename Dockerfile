# syntax=docker/dockerfile:1

# -------------------------------------------------------------
# Etapa 1: build con el wrapper de Maven y JDK 25 (Temurin, no Early Access)
# -------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src ./src

# Cache persistente de ~/.m2 entre builds
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package -DskipTests

# -------------------------------------------------------------
# Etapa 2: imagen de ejecución
# -------------------------------------------------------------
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

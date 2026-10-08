# ============================================================================
# Etapa 1: build
# ----------------------------------------------------------------------------
# El jar se construye acá adentro y no en la máquina de quien buildea. Esa es la
# diferencia que permite que el pipeline corra en un runner limpio: la imagen sale
# siempre del commit, nunca de un working tree con cambios sin commitear.
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# El pom primero y solo: mientras las dependencias no cambien, Docker reusa esta
# capa y no vuelve a descargar medio Maven Central en cada build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src

# Los tests corren antes en el pipeline, con su propio reporte. Repetirlos acá
# duplicaría varios minutos por build sin agregar información.
RUN mvn -B -q clean package -DskipTests

# ============================================================================
# Etapa 2: runtime
# ----------------------------------------------------------------------------
# Idéntica a la imagen que ya venía funcionando: mismo base image, mismo TZ y
# mismo CMD. Lo único que cambia respecto de antes es de dónde sale el jar.
# ============================================================================
FROM eclipse-temurin:21-jdk

WORKDIR /app

# Forzamos UTC a nivel de Sistema Operativo y JVM
ENV TZ=Etc/UTC

COPY --from=build /build/target/mecatron-*.jar /app/openlab.jar

EXPOSE 8080

# Usamos -Duser.timezone=UTC para asegurar que Java no herede nada distinto
CMD ["java", "-Duser.timezone=UTC", "-XX:MaxRAMPercentage=65.0", "-XX:InitialRAMPercentage=25.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/openlab.jar"]

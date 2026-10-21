# syntax=docker/dockerfile:1

# ---------- Etapa 1: compilar con Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# La caché de ~/.m2 evita descargar las dependencias en cada build.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests package \
 && cp target/alex-rifas-api-*.jar /build/app.jar

# ---------- Etapa 2: imagen final, solo JRE ----------
FROM eclipse-temurin:21-jre-alpine
# Usuario sin privilegios: si alguien vulnera la app, no es root dentro del contenedor.
RUN addgroup -S rifas && adduser -S rifas -G rifas
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER rifas
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

# syntax=docker/dockerfile:1
# Multi-stage build do api_agenda (Spring Boot 4.1.0 / Java 25).
# Espelha a estrutura do Dockerfile do azzo-agenda-pro (Quarkus): build Maven em estagio isolado,
# runtime minimo, usuario nao-root. O agente SkyWalking (baixado e verificado por SHA512 no
# original) NAO foi portado nesta etapa — e uma decisao de observability/DevOps fora do escopo de
# security/common e auth; ver MIGRACAO-QUARKUS-SPRING.md.

# Stage 1: build
FROM maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:25-jre
ARG JAR_FILE=api_agenda-*.jar
WORKDIR /deployments
COPY --from=build /workspace/target/${JAR_FILE} /deployments/app.jar

RUN useradd --uid 185 --create-home appuser
USER 185

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /deployments/app.jar"]

# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring spring

COPY --from=build --chown=spring:spring \
    /workspace/target/Know-Your-Colleagues-*.jar /app/application.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]

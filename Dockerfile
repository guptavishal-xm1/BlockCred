# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/blockcred-1.0.0.jar /app/blockcred.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/blockcred.jar", "--spring.profiles.active=pilot"]

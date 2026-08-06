# syntax=docker/dockerfile:1
# 在 Linux 容器中跑后端，以便加载 flight-hub-sdk.jar 内置的 lib/linux-x86_64 JNI。
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY libs ./libs
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/uav-platform-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]

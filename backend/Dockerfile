FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY .mvn ./.mvn
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17.0.15_6-jre-alpine AS runtime
RUN addgroup -S sicost && adduser -S -G sicost sicost
WORKDIR /app
COPY --from=build --chown=sicost:sicost /app/target/sicost.jar /app/sicost.jar
USER sicost
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "/app/sicost.jar"]

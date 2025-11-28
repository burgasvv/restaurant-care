
FROM bellsoft/liberica-openjdk-alpine:24
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .
COPY gradlew.bat .
COPY gradlew .
COPY gradle gradle
COPY src src
RUN ./gradlew clean build -x test

COPY build/libs/*.jar app.jar
EXPOSE 9000

ENTRYPOINT ["java", "-jar", "app.jar"]
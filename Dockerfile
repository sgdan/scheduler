# Front end: Elm
FROM node:12.12.0 as frontend
RUN yarn global add create-elm-app@4.1.2
WORKDIR /app
COPY frontend/elm.json .
COPY frontend/public public/
COPY frontend/src src/
RUN ELM_APP_URL=/scheduler/ elm-app build

# Back end: Micronaut/Kotlin/Gradle
FROM gradle:6.0.1 as backend
WORKDIR /scheduler
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .
# change gradle home folder so cache will be preserved
RUN gradle -g . shadowJar clean
COPY src src
RUN gradle -g . shadowJar
# e.g. /scheduler/build/libs/scheduler-1.0-SNAPSHOT-all.jar

# Final image: OpenJDK
FROM adoptopenjdk/openjdk13:jre-13.0.1_9-alpine
WORKDIR /scheduler
COPY --from=frontend /app/build ./ui
COPY --from=backend /scheduler/build/libs/scheduler-*-all.jar scheduler.jar
ENV CORS_ENABLED false
CMD ["java", "-jar", "scheduler.jar"]

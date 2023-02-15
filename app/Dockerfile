FROM openjdk:17-alpine as build
ARG DOCKER_HOST=tcp://172.17.0.1:2375
WORKDIR /project
COPY ./gradle ./gradle
COPY ./gradlew ./gradlew
COPY ./settings.gradle.kts ./settings.gradle.kts
COPY ./app/build.gradle.kts ./app/build.gradle.kts
COPY ./app/src ./app/src
RUN ./gradlew :app:installDist

FROM openjdk:17-alpine as app
COPY --from=build /project/app/build/install/app app
WORKDIR app
ENTRYPOINT ["bin/app"]
FROM openjdk:17-alpine as build
WORKDIR /project
COPY ./gradle ./gradle
COPY ./gradlew ./gradlew
COPY ./settings.gradle.kts ./settings.gradle.kts
COPY ./cli/build.gradle.kts ./cli/build.gradle.kts
COPY ./cli/src ./cli/src
RUN ./gradlew :cli:installDist

FROM openjdk:17-alpine as app
COPY --from=build /project/cli/build/install/cli app
WORKDIR app
ENTRYPOINT ["bin/cli"]
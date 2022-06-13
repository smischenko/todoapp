FROM openjdk:17-alpine
COPY build/install/todoapp todoapp
WORKDIR todoapp
ENTRYPOINT ["bin/todoapp"]
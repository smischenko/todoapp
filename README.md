# TODOAPP

Todoapp is a simple application to manage list of todo tasks.

The aim of this project is to try various technologies. The project uses:

- **Kotlin** as programming language
- **Ktor** as http server
- **Postgresql** as database
- **Jooq** as SQL library
- **Flyway** as database migration tool
- **Zipkin** as tracing tool
- **Prometheus** as metrics storage
- **Grafana** as metrics visualisation tool
- **Kotest** as test tool
- **Wiremock**
- **Testcontainers** 

# How to start

    ./gradlew clean installDist
    docker compose up --build -d

Then try

    curl http://localhost/todo

# REST API

Application does not have UI. It provides REST API only.

- `GET /todo` - read todo task list
- `POST /todo` - create todo task
- `PUT /todo/{id}` - update todo task
- `DELETE /todo/{id}` - delete todo task

For extended examples look at `todo.http` file.
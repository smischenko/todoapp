package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import todoapp.domain.TodoDeleteRequest
import todoapp.domain.TodoDeleteUseCase

fun todoDeleteRoute(todoDeleteUseCase: TodoDeleteUseCase): Route.() -> Unit = {
    delete("/todo/{id}") {
        call.parameters["id"]?.toIntOrNull()?.let { id ->
            todoDeleteUseCase(TodoDeleteRequest(id))
        }
        call.respond(HttpStatusCode.OK)
    }
}
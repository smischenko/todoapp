package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import todoapp.domain.TodoReadUseCase

fun todoReadRoute(todoReadUseCase: TodoReadUseCase): Route.() -> Unit = {
    get("/todo") {
        val todoList = todoReadUseCase()
        call.respond(HttpStatusCode.OK, TodoReadResponse(todoList.map { it.toView() }))
    }
}

@Serializable
data class TodoReadResponse(val todo: List<TodoView>)
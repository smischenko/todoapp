package todoapp.infrastructure

import arrow.core.raise.recover
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import todoapp.domain.TodoCreateUseCase
import todoapp.domain.TodoCreateRequest as UseCaseRequest

fun todoCreateRoute(todoCreateUseCase: TodoCreateUseCase): Route.() -> Unit = {
    post("/todo") {
        recover({
            val request = call.receiveCatching<TodoCreateRequest>().bind()
            val todo = todoCreateUseCase(UseCaseRequest(request.todo.text))
            call.respond(Created, TodoCreateResponse(todo.toView()))
        }) { call.respondError(it) }
    }
}

@Serializable
data class TodoCreateRequest(val todo: TodoCreate)

@Serializable
data class TodoCreate(val text: String)

@Serializable
data class TodoCreateResponse(val todo: TodoView)
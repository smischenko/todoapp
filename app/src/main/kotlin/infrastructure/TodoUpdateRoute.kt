package todoapp.infrastructure

import arrow.core.getOrElse
import arrow.core.raise.recover
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import todoapp.domain.TodoUpdateUseCase
import todoapp.infrastructure.HttpError.Companion.notFound
import todoapp.domain.TodoUpdateRequest as UseCaseRequest

fun todoUpdateRoute(todoUpdateUseCase: TodoUpdateUseCase): Route.() -> Unit = {
    put("/todo/{id}") {
        recover({
            val id: Int = call.parameters["id"]?.toIntOrNull() ?: raise(notFound("Todo not found"))
            val request = call.receiveCatching<TodoUpdateRequest>().bind()
            val todo = todoUpdateUseCase(UseCaseRequest(id, request.todo.text, request.todo.done))
                .getOrElse { raise(it.toHttpError()) }
            call.respond(HttpStatusCode.OK, TodoUpdateResponse(todo.toView()))
        }) { call.respondError(it) }
    }
}

@Serializable
data class TodoUpdateRequest(val todo: TodoUpdate)

@Serializable
data class TodoUpdate(val text: String? = null, val done: Boolean? = null)

@Serializable
data class TodoUpdateResponse(val todo: TodoView)
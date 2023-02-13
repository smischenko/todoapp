package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import todoapp.*
import todoapp.domain.TodoUpdateUseCase
import todoapp.domain.TodoUpdateRequest as UseCaseRequest

typealias TodoUpdateHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoUpdateHandler(todoUpdateUseCase: TodoUpdateUseCase): TodoUpdateHandler = {
    withErrorHandling {
        val id: Int = call.parameters["id"]?.toIntOrNull() ?: shift(Error.TodoNotFound)
        val request = call.receiveCatching<TodoUpdateRequest>().bind()
        val todo = todoUpdateUseCase(UseCaseRequest(id, request.todo.text, request.todo.done)).bind()
        call.respond(HttpStatusCode.OK, TodoUpdateResponse(todo.toView()))
    }
}

@Serializable
data class TodoUpdateRequest(val todo: TodoUpdate)

@Serializable
data class TodoUpdate(val text: String? = null, val done: Boolean? = null)

@Serializable
data class TodoUpdateResponse(val todo: TodoView)
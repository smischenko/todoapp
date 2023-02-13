package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import todoapp.*
import todoapp.domain.TodoCreateUseCase
import todoapp.domain.TodoCreateRequest as UseCaseRequest

typealias TodoCreateHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoCreateHandler(todoCreateUseCase: TodoCreateUseCase): TodoCreateHandler = {
    withErrorHandling {
        val request = call.receiveCatching<TodoCreateRequest>().bind()
        val todo = todoCreateUseCase(UseCaseRequest(request.todo.text))
        call.respond(HttpStatusCode.Created, TodoCreateResponse(todo.toView()))
    }
}

@Serializable
data class TodoCreateRequest(val todo: TodoCreate)

@Serializable
data class TodoCreate(val text: String)

@Serializable
data class TodoCreateResponse(val todo: TodoView)
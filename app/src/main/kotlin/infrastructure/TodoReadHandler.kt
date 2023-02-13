package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import todoapp.domain.TodoReadUseCase

typealias TodoReadHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoReadHandler(todoReadUseCase: TodoReadUseCase): TodoReadHandler = {
    val todoList = todoReadUseCase()
    call.respond(HttpStatusCode.OK, TodoReadResponse(todoList.map { it.toView() }))
}

@Serializable
data class TodoReadResponse(val todo: List<TodoView>)
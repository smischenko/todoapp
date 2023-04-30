package todoapp.infrastructure

import arrow.core.continuations.effect
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable
import todoapp.domain.TodoReadUseCase

typealias TodoReadHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoReadHandler(todoReadUseCase: TodoReadUseCase): TodoReadHandler = {
    effect {
        val todoList = todoReadUseCase().mapLeft { it.toHttpError() }.bind()
        call.respond(HttpStatusCode.OK, TodoReadResponse(todoList.map { it.toView() }))
    }.handleError { call.respondError(it) }.fold()
}

@Serializable
data class TodoReadResponse(val todo: List<TodoView>)
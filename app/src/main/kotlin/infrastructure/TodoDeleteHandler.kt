package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import todoapp.domain.TodoDeleteRequest
import todoapp.domain.TodoDeleteUseCase

typealias TodoDeleteHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoDeleteHandler(todoDeleteUseCase: TodoDeleteUseCase): TodoDeleteHandler = {
    call.parameters["id"]?.toIntOrNull()?.let { id ->
        todoDeleteUseCase(TodoDeleteRequest(id))
    }
    call.respond(HttpStatusCode.OK)
}
package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import todoapp.*

typealias TodoUpdateHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoUpdateHandler(service: Service): TodoUpdateHandler = {
    withErrorHandling {
        val id: Int = call.parameters["id"]?.toIntOrNull() ?: shift(Error.TodoNotFound)
        val request = call.receiveCatching<TodoUpdateRequest>().bind()
        val todo = service.todoUpdate(id, request.todo).bind()
        call.respond(HttpStatusCode.OK, TodoUpdateResponse(todo))
    }
}
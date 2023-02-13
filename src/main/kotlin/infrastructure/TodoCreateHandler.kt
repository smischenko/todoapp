package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import todoapp.*

typealias TodoCreateHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoCreateHandler(service: Service): TodoCreateHandler = {
    withErrorHandling {
        val request = call.receiveCatching<TodoCreateRequest>().bind()
        val todo = service.todoCreate(request.todo)
        call.respond(HttpStatusCode.Created, TodoCreateResponse(todo))
    }
}
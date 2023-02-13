package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import todoapp.Service
import todoapp.TodoReadResponse

typealias TodoReadHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoReadHandler(service: Service): TodoReadHandler = {
    val todo = service.todoRead()
    call.respond(HttpStatusCode.OK, TodoReadResponse(todo))
}
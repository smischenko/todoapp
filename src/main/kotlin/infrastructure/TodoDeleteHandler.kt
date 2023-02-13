package todoapp.infrastructure

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import todoapp.Service

typealias TodoDeleteHandler = PipelineInterceptor<Unit, ApplicationCall>

fun todoDeleteHandler(service: Service): TodoDeleteHandler = {
    call.parameters["id"]?.toIntOrNull()?.let { id -> service.todoDelete(id) }
    call.respond(HttpStatusCode.OK)
}
package todoapp.infrastructure

import io.ktor.http.*
import todoapp.domain.DomainError
import todoapp.infrastructure.HttpError.Companion.notFound

data class HttpError(val statusCode: HttpStatusCode, val message: String) {
    companion object {
        fun notFound(message: String) = HttpError(HttpStatusCode.NotFound, message)
        fun badRequest(message: String) = HttpError(HttpStatusCode.BadRequest, message)
    }
}

fun DomainError.toHttpError(): HttpError =
    when (this) {
        is DomainError.TodoNotFound -> notFound("Todo not found")
    }
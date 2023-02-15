package todoapp.infrastructure

import arrow.core.Either
import arrow.core.continuations.Effect
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import todoapp.infrastructure.HttpError.Companion.badRequest

suspend inline fun <reified T : Any> ApplicationCall.receiveCatching(): Either<HttpError, T> =
    Either.catch { receive<T>() }.mapLeft { badRequest(it.message ?: "Can not receive request") }

suspend fun ApplicationCall.respondError(error: HttpError): Unit =
    respond(error.statusCode, error.message)

suspend fun Effect<Nothing, Unit>.run(): Unit = fold({}, {})
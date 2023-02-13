package todoapp

import arrow.core.Either
import arrow.core.continuations.Effect
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import todoapp.Error.RequestReceiveError
import todoapp.Error.TodoNotFound

suspend inline fun <reified T : Any> ApplicationCall.receiveCatching(): Either<RequestReceiveError, T> =
    Either.catch { receive<T>() }.mapLeft { RequestReceiveError(it.message ?: "Can not receive request") }

suspend fun ApplicationCall.respondError(error: Error): Unit =
    when (error) {
        is RequestReceiveError -> respond(BadRequest, mapOf("message" to error.message))
        is TodoNotFound -> respond(NotFound)
    }

sealed class Error {
    data class RequestReceiveError(val message: String) : Error()
    object TodoNotFound : Error()
}

suspend inline fun PipelineContext<Unit, ApplicationCall>.withErrorHandling(noinline block: suspend EffectScope<Error>.() -> Unit): Unit =
    effect(block).handleError { call.respondError(it) }.run()

suspend fun Effect<Nothing, Unit>.run(): Unit = fold({}, {})

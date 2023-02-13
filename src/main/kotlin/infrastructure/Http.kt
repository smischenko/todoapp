package todoapp.infrastructure

import arrow.core.Either
import arrow.core.continuations.Effect
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import todoapp.domain.Error

suspend inline fun <reified T : Any> ApplicationCall.receiveCatching(): Either<Error.RequestReceiveError, T> =
    Either.catch { receive<T>() }.mapLeft { Error.RequestReceiveError(it.message ?: "Can not receive request") }

suspend fun ApplicationCall.respondError(error: Error): Unit =
    when (error) {
        is Error.RequestReceiveError -> respond(HttpStatusCode.BadRequest, mapOf("message" to error.message))
        is Error.TodoNotFound -> respond(HttpStatusCode.NotFound)
    }

suspend inline fun PipelineContext<Unit, ApplicationCall>.withErrorHandling(noinline block: suspend EffectScope<Error>.() -> Unit): Unit =
    effect(block).handleError { call.respondError(it) }.run()

suspend fun Effect<Nothing, Unit>.run(): Unit = fold({}, {})
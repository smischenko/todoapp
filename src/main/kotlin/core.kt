package todoapp

import arrow.core.Either
import arrow.core.continuations.Effect
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.jooq.Record
import todoapp.Error.RequestReceiveError
import todoapp.Error.TodoNotFound
import todoapp.domain.Todo
import todoapp.jooq.tables.references.TODO

class Repository {

    context(TransactionContext) suspend fun selectTodoCount(): Int = withContext(IO) {
        jooq.selectCount().from(TODO).fetchSingle().value1()
    }

    context (TransactionContext) suspend fun selectAllTodo(): List<Todo> = withContext(IO) {
        jooq.select()
            .from(TODO)
            .orderBy(TODO.INDEX)
            .fetch { it.toTodo() }
    }

    context (TransactionContext) suspend fun selectTodo(id: Int): Todo? = withContext(IO) {
        jooq.select()
            .from(TODO)
            .where(TODO.ID.eq(id))
            .fetchOne { it.toTodo() }
    }

    context (TransactionContext) suspend fun insertTodo(todo: Todo): Int = withContext(IO) {
        jooq.insertInto(TODO)
            .set(TODO.TEXT, todo.text)
            .set(TODO.DONE, todo.done)
            .set(TODO.INDEX, todo.index)
            .returning(TODO.ID)
            .execute()
    }

    context (TransactionContext) suspend fun updateTodo(entity: Todo): Unit = withContext(IO) {
        jooq.update(TODO)
            .set(TODO.TEXT, entity.text)
            .set(TODO.DONE, entity.done)
            .set(TODO.INDEX, entity.index)
            .where(TODO.ID.eq(entity.id))
            .execute()
    }

    context (TransactionContext) suspend fun updateTodo(entities: List<Todo>): Unit = withContext(IO) {
        if (entities.isNotEmpty()) {
            val batch = jooq.batch(
                jooq.update(TODO)
                    .set(TODO.TEXT, null as String?)
                    .set(TODO.DONE, null as Boolean?)
                    .set(TODO.INDEX, null as Int?)
                    .where(TODO.ID.eq(null as Int?))
            )
            entities.forEach { entity ->
                batch.bind(entity.text, entity.done, entity.index, entity.id)
            }
            batch.execute()
        }
    }

    context (TransactionContext) suspend fun deleteTodo(id: Int): Unit = withContext(IO) {
        jooq.deleteFrom(TODO)
            .where(TODO.ID.eq(id))
            .execute()
    }

    private fun Record.toTodo() =
        Todo(
            id = this[TODO.ID]!!,
            text = this[TODO.TEXT]!!,
            done = this[TODO.DONE]!!,
            index = this[TODO.INDEX]!!
        )
}

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

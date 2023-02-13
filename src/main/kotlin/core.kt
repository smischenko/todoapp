package todoapp

import arrow.core.Either
import arrow.core.continuations.Effect
import arrow.core.continuations.EffectScope
import arrow.core.continuations.effect
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Created
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jooq.Record
import todoapp.Error.RequestReceiveError
import todoapp.Error.TodoNotFound
import todoapp.TransactionIsolation.REPEATABLE_READ
import todoapp.TransactionIsolation.SERIALIZABLE
import todoapp.jooq.tables.references.TODO

class Service(private val transaction: Transaction, private val repository: Repository) {

    suspend fun todoCreate(todoCreate: TodoCreate): Todo =
        transaction(isolation = SERIALIZABLE) {
            val todoCount = repository.selectTodoCount()
            val entity = TodoEntity(
                id = 0, // set 0 as id is a trick to not break nonnullable id declaration
                text = todoCreate.text.trim(),
                done = false,
                index = todoCount
            )
            val id = repository.insertTodo(entity)
            entity.copy(id = id).toTodo()
        }

    suspend fun todoRead(): List<Todo> =
        transaction(isolation = REPEATABLE_READ, readOnly = true) {
            repository.selectAllTodo().sortedBy { it.index }.map { it.toTodo() }
        }

    suspend fun todoUpdate(id: Int, todoUpdate: TodoUpdate): Either<TodoNotFound, Todo> =
        transaction(isolation = SERIALIZABLE) {
            repository.selectTodo(id)?.let { entity ->
                val updated = entity.apply(todoUpdate)
                repository.updateTodo(updated)
                updated.toTodo().right()
            } ?: TodoNotFound.left()
        }

    private fun TodoEntity.apply(todoUpdate: TodoUpdate): TodoEntity {
        var result = this
        result = todoUpdate.text?.let { result.copy(text = it.trim()) } ?: result
        result = todoUpdate.done?.let { result.copy(done = it) } ?: result
        return result
    }

    suspend fun todoDelete(id: Int): Unit =
        transaction(isolation = SERIALIZABLE) {
            repository.selectTodo(id)?.let { entity ->
                repository.deleteTodo(id)
                val tail = repository.selectAllTodo().filter { it.index > entity.index }
                val tailUpdated = tail.map { it.copy(index = it.index - 1) }
                repository.updateTodo(tailUpdated)
            }
        }

    private fun TodoEntity.toTodo() = Todo(id, text, done, index)
}

class Repository {

    context(TransactionContext) suspend fun selectTodoCount(): Int = withContext(IO) {
        jooq.selectCount().from(TODO).fetchSingle().value1()
    }

    context(TransactionContext) suspend fun selectAllTodo(): List<TodoEntity> = withContext(IO) {
        jooq.select()
            .from(TODO)
            .fetch { it.toTodoEntity() }
    }

    context (TransactionContext) suspend fun selectTodo(id: Int): TodoEntity? = withContext(IO) {
        jooq.select()
            .from(TODO)
            .where(TODO.ID.eq(id))
            .fetchOne { it.toTodoEntity() }
    }

    context (TransactionContext) suspend fun insertTodo(entity: TodoEntity): Int = withContext(IO) {
        jooq.insertInto(TODO)
            .set(TODO.TEXT, entity.text)
            .set(TODO.DONE, entity.done)
            .set(TODO.INDEX, entity.index)
            .returning(TODO.ID)
            .execute()
    }

    context (TransactionContext) suspend fun updateTodo(entity: TodoEntity): Unit = withContext(IO) {
        jooq.update(TODO)
            .set(TODO.TEXT, entity.text)
            .set(TODO.DONE, entity.done)
            .set(TODO.INDEX, entity.index)
            .where(TODO.ID.eq(entity.id))
            .execute()
    }

    context (TransactionContext) suspend fun updateTodo(entities: List<TodoEntity>): Unit = withContext(IO) {
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

    private fun Record.toTodoEntity() =
        TodoEntity(
            id = this[TODO.ID]!!,
            text = this[TODO.TEXT]!!,
            done = this[TODO.DONE]!!,
            index = this[TODO.INDEX]!!
        )
}

data class TodoEntity(
    val id: Int,
    val text: String,
    val done: Boolean,
    val index: Int
)

@Serializable
data class TodoReadResponse(val todo: List<Todo>)

@Serializable
data class Todo(
    val id: Int,
    val text: String,
    val done: Boolean,
    val index: Int
)

@Serializable
data class TodoCreateRequest(val todo: TodoCreate)

@Serializable
data class TodoCreate(val text: String)

@Serializable
data class TodoCreateResponse(val todo: Todo)

@Serializable
data class TodoUpdateRequest(val todo: TodoUpdate)

@Serializable
data class TodoUpdate(val text: String? = null, val done: Boolean? = null)

@Serializable
data class TodoUpdateResponse(val todo: Todo)

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

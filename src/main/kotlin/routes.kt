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
import todoapp.jooq.tables.references.TODO
import java.sql.Connection
import java.sql.Connection.TRANSACTION_REPEATABLE_READ
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import javax.sql.DataSource

typealias Routes = List<Route.() -> Unit>

context(Routing)
operator fun Routes.invoke() = with(this@Routing) {
    forEach { it() }
}

fun routes(dataSource: DataSource): Routes =
    listOf(
        todoCreateRoute(todoCreate(dataSource)),
        todoReadRoute(todoRead(dataSource)),
        todoUpdateRoute(todoUpdate(dataSource)),
        todoDeleteRoute(todoDelete(dataSource)),
    )

fun todoCreateRoute(todoCreate: suspend (TodoCreate) -> Todo): Route.() -> Unit = {
    post("/todo") {
        withErrorHandling {
            val request = call.receiveCatching<TodoCreateRequest>().bind()
            val todo = todoCreate(request.todo)
            call.respond(Created, TodoCreateResponse(todo))
        }
    }
}

fun todoReadRoute(todoRead: suspend () -> List<Todo>): Route.() -> Unit = {
    get("/todo") {
        val todo = todoRead()
        call.respond(OK, TodoReadResponse(todo))
    }
}

fun todoUpdateRoute(todoUpdate: suspend (Int, TodoUpdate) -> Either<Error, Todo>): Route.() -> Unit = {
    put("/todo/{id}") {
        withErrorHandling {
            val id: Int = call.parameters["id"]?.toIntOrNull() ?: shift(TodoNotFound)
            val request = call.receiveCatching<TodoUpdateRequest>().bind()
            val todo = todoUpdate(id, request.todo).bind()
            call.respond(OK, TodoUpdateResponse(todo))
        }
    }
}

fun todoDeleteRoute(todoDelete: suspend (Int) -> Unit): Route.() -> Unit = {
    delete("/todo/{id}") {
        call.parameters["id"]?.toIntOrNull()?.let { id -> todoDelete(id) }
        call.respond(OK)
    }
}

fun todoCreate(dataSource: DataSource): suspend (TodoCreate) -> Todo =
    { todoCreate ->
        withContext(IO) {
            dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
                val todoCount = selectTodoCount()
                val entity = TodoEntity(
                    id = 0, // set 0 as id is a trick to not break nonnullable id declaration
                    text = todoCreate.text.trim(),
                    done = false,
                    index = todoCount
                )
                val id = insertTodo(entity)
                entity.copy(id = id).toTodo()
            }
        }
    }

fun todoRead(dataSource: DataSource): suspend () -> List<Todo> =
    {
        withContext(IO) {
            dataSource.transactional(transactionIsolation = TRANSACTION_REPEATABLE_READ, readOnly = true) {
                selectAllTodo().sortedBy { it.index }.map { it.toTodo() }
            }
        }
    }

fun todoUpdate(dataSource: DataSource): suspend (Int, TodoUpdate) -> Either<TodoNotFound, Todo> =
    { id, todoUpdate ->
        withContext(IO) {
            dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
                selectTodo(id)?.let { entity ->
                    val updated = entity.apply(todoUpdate)
                    updateTodo(updated)
                    updated.toTodo().right()
                } ?: TodoNotFound.left()
            }
        }
    }

fun TodoEntity.apply(todoUpdate: TodoUpdate): TodoEntity {
    var result = this
    result = todoUpdate.text?.let { result.copy(text = it.trim()) } ?: result
    result = todoUpdate.done?.let { result.copy(done = it) } ?: result
    return result
}

fun todoDelete(dataSource: DataSource): suspend (Int) -> Unit =
    { id ->
        withContext(IO) {
            dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
                selectTodo(id)?.let { entity ->
                    deleteTodo(id)
                    val tail = selectAllTodo().filter { it.index > entity.index }
                    val tailUpdated = tail.map { it.copy(index = it.index - 1) }
                    updateTodo(tailUpdated)
                }
            }
        }
    }

data class TodoEntity(
    val id: Int,
    val text: String,
    val done: Boolean,
    val index: Int
)

fun Connection.selectTodoCount(): Int =
    jooq.selectCount().from(TODO).fetchSingle().value1()

fun Connection.selectAllTodo(): List<TodoEntity> =
    jooq.select()
        .from(TODO)
        .fetch(Record::toTodoEntity)

fun Connection.selectTodo(id: Int): TodoEntity? =
    jooq.select()
        .from(TODO)
        .where(TODO.ID.eq(id))
        .fetchOne(Record::toTodoEntity)

fun Record.toTodoEntity() =
    TodoEntity(
        id = this[TODO.ID]!!,
        text = this[TODO.TEXT]!!,
        done = this[TODO.DONE]!!,
        index = this[TODO.INDEX]!!
    )

fun Connection.insertTodo(entity: TodoEntity): Int =
    jooq.insertInto(TODO)
        .set(TODO.TEXT, entity.text)
        .set(TODO.DONE, entity.done)
        .set(TODO.INDEX, entity.index)
        .returning(TODO.ID)
        .execute()

fun Connection.updateTodo(entity: TodoEntity) {
    jooq.update(TODO)
        .set(TODO.TEXT, entity.text)
        .set(TODO.DONE, entity.done)
        .set(TODO.INDEX, entity.index)
        .where(TODO.ID.eq(entity.id))
        .execute()
}

fun Connection.updateTodo(entities: List<TodoEntity>) {
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

fun Connection.deleteTodo(id: Int) {
    jooq.deleteFrom(TODO)
        .where(TODO.ID.eq(id))
        .execute()
}

private fun TodoEntity.toTodo() = Todo(id, text, done, index)

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
    data class RequestReceiveError(val message: String): Error()
    object TodoNotFound : Error()
}

suspend inline fun PipelineContext<Unit, ApplicationCall>.withErrorHandling(noinline block: suspend EffectScope<Error>.() -> Unit): Unit =
    effect(block).handleError { call.respondError(it) }.run()

suspend fun Effect<Nothing, Unit>.run(): Unit = fold({}, {})

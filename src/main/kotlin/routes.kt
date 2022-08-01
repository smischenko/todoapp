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
import todoapp.Error.RequestReceiveError
import todoapp.Error.TodoNotFound
import todoapp.jooq.tables.records.TodoRecord
import todoapp.jooq.tables.references.TODO
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
                val count = jooq.selectCount().from(TODO).fetchSingle().value1()
                val record = jooq.newRecord(TODO).apply {
                    text = todoCreate.text.trim()
                    done = false
                    index = count
                }
                record.store()
                record.toTodo()
            }
        }
    }

fun todoRead(dataSource: DataSource): suspend () -> List<Todo> =
    {
        withContext(IO) {
            dataSource.transactional(transactionIsolation = TRANSACTION_REPEATABLE_READ, readOnly = true) {
                jooq.fetch(TODO).map { it.toTodo() }.sortedBy { it.index }
            }
        }
    }

fun todoUpdate(dataSource: DataSource): suspend (Int, TodoUpdate) -> Either<TodoNotFound, Todo> =
    { id, todoUpdate ->
        withContext(IO) {
            dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
                jooq.fetchOne(TODO, TODO.ID.eq(id))?.let { record ->
                    todoUpdate.text?.let { record.text = it.trim() }
                    todoUpdate.done?.let { record.done = it }
                    record.store()
                    record.toTodo().right()
                } ?: TodoNotFound.left()
            }
        }
    }

fun todoDelete(dataSource: DataSource): suspend (Int) -> Unit =
    { id ->
        withContext(IO) {
            dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
                jooq.fetchOne(TODO, TODO.ID.eq(id))?.let { record ->
                    record.delete()
                    val tail = jooq.fetch(TODO, TODO.INDEX.greaterThan(record.index))
                    tail.forEach { it.index = it.index!! - 1 }
                    jooq.batchStore(tail).execute()
                }
            }
        }
    }

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

private fun TodoRecord.toTodo() = Todo(id!!, text!!, done!!, index!!)

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

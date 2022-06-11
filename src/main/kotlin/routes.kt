package todoapp

import arrow.core.Either
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
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import todoapp.jooq.tables.records.TodoRecord
import todoapp.jooq.tables.references.TODO
import java.sql.Connection.TRANSACTION_REPEATABLE_READ
import java.sql.Connection.TRANSACTION_SERIALIZABLE
import javax.sql.DataSource

class Routes(private val dataSource: DataSource) {

    fun Route.todoCreateRoute() {
        post("/todo") {
            processApiCall {
                val request = call.receiveCatching<TodoCreateRequest>().bind()
                val todo = todoCreate(request.todo)
                call.respond(Created, TodoCreateResponse(todo))
            }
        }
    }

    fun Route.todoReadRoute() {
        get("/todo") {
            processApiCall {
                val todo = todoRead()
                call.respond(OK, TodoReadResponse(todo))
            }
        }
    }

    fun Route.todoUpdateRoute() {
        put("/todo/{id}") {
            processApiCall {
                val id = call.parameters["id"]?.toIntOrNull() ?: shift(ApiError.TodoNotFound)
                val request = call.receiveCatching<TodoUpdateRequest>().bind()
                val todo = todoUpdate(id, request.todo).bind()
                call.respond(OK, TodoUpdateResponse(todo))
            }
        }
    }

    fun Route.todoDeleteRoute() {
        delete("/todo/{id}") {
            processApiCall {
                call.parameters["id"]?.toIntOrNull()?.let { id -> todoDelete(id) }
                call.respond(OK)
            }
        }
    }

    private suspend fun todoCreate(todoCreate: TodoCreate): Todo = withContext(IO) {
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

    private suspend fun todoRead(): List<Todo> = withContext(IO) {
        dataSource.transactional(transactionIsolation = TRANSACTION_REPEATABLE_READ, readOnly = true) {
            jooq.fetch(TODO).map { it.toTodo() }.sortedBy { it.index }
        }
    }

    private suspend fun todoUpdate(id: Int, todoUpdate: TodoUpdate): Either<ApiError.TodoNotFound, Todo> = withContext(IO) {
        dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
            jooq.fetchOne(TODO, TODO.ID.eq(id))?.let { record ->
                todoUpdate.text?.let { record.text = it.trim() }
                todoUpdate.done?.let { record.done = it }
                record.store()
                record.toTodo().right()
            } ?: ApiError.TodoNotFound.left()
        }
    }

    private suspend fun todoDelete(id: Int): Unit = withContext(IO) {
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

context(Routing)
operator fun Routes.invoke() {
    todoCreateRoute()
    todoReadRoute()
    todoUpdateRoute()
    todoDeleteRoute()
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

suspend inline fun CallContext.processApiCall(crossinline block: suspend EffectScope<ApiError>.() -> Unit): Unit =
    effect(block).fold({ call.respondError(it) }, { })

suspend inline fun <reified T : Any> ApplicationCall.receiveCatching(): Either<ApiError, T> =
    Either.catch { receive<T>() }.mapLeft { ApiError.BadRequest(it.message ?: "Can not receive request") }

suspend fun ApplicationCall.respondError(error: ApiError) {
    when (error) {
        is ApiError.BadRequest -> respond(BadRequest, mapOf("message" to error.message))
        is ApiError.TodoNotFound -> respond(NotFound)
    }
}

sealed class ApiError {
    data class BadRequest(val message: String) : ApiError()
    object TodoNotFound : ApiError()
}

typealias CallContext = PipelineContext<Unit, ApplicationCall>
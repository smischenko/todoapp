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
import org.jooq.Record
import java.sql.Connection
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

    private suspend fun todoCreate(todoCreate: TodoCreate): Todo =
        dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
            val id = nextTodoId()
            val count = selectTodoCount()
            val todo = Todo(
                id = id,
                text = todoCreate.text.trim(),
                done = false,
                index = count
            )
            insertTodo(todo)
            todo
        }

    private suspend fun todoRead(): List<Todo> =
        dataSource.transactional(transactionIsolation = TRANSACTION_REPEATABLE_READ, readOnly = true) {
            selectTodo()
        }

    private suspend fun todoUpdate(id: Int, todoUpdate: TodoUpdate): Either<ApiError.TodoNotFound, Todo> =
        dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
            selectTodo(id)?.let { todo ->
                val updated = todo.copy(
                    text = todoUpdate.text?.trim() ?: todo.text,
                    done = todoUpdate.done ?: todo.done
                )
                updateTodo(updated)
                updated.right()
            } ?: ApiError.TodoNotFound.left()
        }

    private suspend fun todoDelete(id: Int): Unit =
        dataSource.transactional(transactionIsolation = TRANSACTION_SERIALIZABLE) {
            selectTodo(id)?.let { todo ->
                deleteTodo(id)
                val tail = selectTodoFromIndex(todo.index + 1)
                val tailUpdated = tail.map { it.copy(index = it.index - 1) }
                updateTodo(tailUpdated)
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

private suspend fun Connection.nextTodoId(): Int = withContext(IO) {
    jooq.select(TODO_ID_SEQ.nextval())
        .fetchSingle().value1()
}

private suspend fun Connection.selectTodo(): List<Todo> = withContext(IO) {
    jooq.select(ID, TEXT, DONE, INDEX)
        .from(TODO)
        .orderBy(INDEX)
        .fetch { it.toTodo() }
}

private suspend fun Connection.selectTodo(id: Int): Todo? = withContext(IO) {
    jooq.select(ID, TEXT, DONE, INDEX)
        .from(TODO)
        .where(ID.eq(id))
        .fetchOne { it.toTodo() }
}

private suspend fun Connection.selectTodoFromIndex(index: Int): List<Todo> = withContext(IO) {
    jooq.select(ID, TEXT, DONE, INDEX)
        .from(TODO)
        .where(INDEX.ge(index))
        .fetch { it.toTodo() }
}

private suspend fun Connection.selectTodoCount(): Int = withContext(IO) {
    jooq.selectCount()
        .from(TODO)
        .fetchSingle().value1()
}

private suspend fun Connection.insertTodo(todo: Todo): Unit = withContext(IO) {
    jooq.insertInto(TODO)
        .set(ID, todo.id)
        .set(TEXT, todo.text)
        .set(DONE, todo.done)
        .set(INDEX, todo.index)
        .execute()
}

private suspend fun Connection.updateTodo(todo: Todo): Unit = withContext(IO) {
    jooq.update(TODO)
        .set(TEXT, todo.text)
        .set(DONE, todo.done)
        .set(INDEX, todo.index)
        .where(ID.eq(todo.id))
        .execute()
}

private suspend fun Connection.updateTodo(todo: List<Todo>): Unit = withContext(IO) {
    todo.takeUnless { it.isEmpty() }?.let { todo ->
        val batch = jooq.batch(
            jooq.update(TODO)
                .set(TEXT, null as String?)
                .set(DONE, null as Boolean?)
                .set(INDEX, null as Int?)
                .where(ID.eq(null as Int?))
        )
        for (it in todo) {
            batch.bind(it.text, it.done, it.index, it.id)
        }
        batch.execute()
    }
}

private suspend fun Connection.deleteTodo(id: Int): Unit = withContext(IO) {
    jooq.deleteFrom(TODO)
        .where(ID.eq(id))
        .execute()
}

fun Record.toTodo() =
    Todo(
        id = this[ID],
        text = this[TEXT],
        done = this[DONE],
        index = this[INDEX],
    )

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
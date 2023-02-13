@file:OptIn(ExperimentalCli::class)

package todoapp.cli

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

fun main(args: Array<String>) {
    val httpClient = httpClient()
    val createCommand = createCommand(httpClient)
    val listCommand = listCommand(httpClient)
    val doneCommand = doneCommand(httpClient)
    val deleteCommand = deleteCommand(httpClient)
    val parser = ArgParser("todoapp")
    parser.subcommands(
        createCommand,
        listCommand,
        doneCommand,
        deleteCommand,
    )
    parser.parse(args)
}

fun httpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
}

fun createCommand(httpClient: HttpClient): Subcommand =
    object : Subcommand("create", "Create new todo") {
        val text by argument(ArgType.String)
        override fun execute() = runBlocking {
            val response: TodoCreateResponse = httpClient.post("http://todoapp/todo") {
                contentType(ContentType.Application.Json)
                setBody(TodoCreateRequest(TodoCreate(text)))
            }.body()
            response.todo.print()
        }
    }

fun listCommand(httpClient: HttpClient): Subcommand =
    object : Subcommand("list", "List todo") {
        override fun execute() = runBlocking {
            val response: TodoListResponse = httpClient.get("http://todoapp/todo").body()
            if (response.todo.isEmpty()) {
                println("No todo")
            } else {
                response.todo.forEach {
                    it.print()
                }
            }
        }
    }

fun doneCommand(httpClient: HttpClient): Subcommand =
    object : Subcommand("done", "Mark todo done") {
        val id by argument(ArgType.Int)
        override fun execute() = runBlocking {
            val response: TodoUpdateResponse = httpClient.put("http://todoapp/todo/$id") {
                contentType(ContentType.Application.Json)
                setBody(TodoUpdateRequest(TodoUpdate(done = true)))
            }.body()
            response.todo.print()
        }
    }

fun deleteCommand(httpClient: HttpClient): Subcommand =
    object : Subcommand("delete", "Delete todo") {
        val id by argument(ArgType.Int)
        override fun execute() = runBlocking {
            httpClient.delete("http://todoapp/todo/$id")
            println("Ok")
        }
    }

@Serializable
data class TodoCreateRequest(val todo: TodoCreate)

@Serializable
data class TodoCreate(val text: String)

@Serializable
data class TodoCreateResponse(val todo: Todo)

@Serializable
data class TodoListResponse(val todo: List<Todo>)

@Serializable
data class TodoUpdateRequest(val todo: TodoUpdate)

@Serializable
data class TodoUpdate(val done: Boolean)

@Serializable
data class TodoUpdateResponse(val todo: Todo)

@Serializable
data class Todo(
    val id: Int,
    val text: String,
    val done: Boolean,
    val index: Int
)

fun Todo.print() = println("[${if (this.done) "v" else " "}] ${this.id}: ${this.text}")
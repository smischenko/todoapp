package todoapp.infrastructure

import io.ktor.server.routing.*

fun routing(
    todoCreateHandler: TodoCreateHandler,
    todoReadHandler: TodoReadHandler,
    todoUpdateHandler: TodoUpdateHandler,
    todoDeleteHandler: TodoDeleteHandler,
): Routing.() -> Unit = {
    post("/todo", todoCreateHandler)
    get("/todo", todoReadHandler)
    put("/todo/{id}", todoUpdateHandler)
    delete("/todo/{id}", todoDeleteHandler)
}
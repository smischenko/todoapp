package todoapp.infrastructure

import kotlinx.serialization.Serializable
import todoapp.domain.Todo

@Serializable
data class TodoView(
    val id: Int,
    val text: String,
    val done: Boolean,
    val index: Int
)

fun Todo.toView(): TodoView = TodoView(
    id = id,
    text = text,
    done = done,
    index = index
)
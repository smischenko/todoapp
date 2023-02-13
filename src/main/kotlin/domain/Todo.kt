package todoapp.domain

data class Todo(
    val id: Int,
    val text: String,
    val done: Boolean,
    val index: Int
)

package todoapp.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right

typealias TodoUpdateUseCase = suspend (TodoUpdateRequest) -> Either<DomainError, Todo>

data class TodoUpdateRequest(
    val id: Int,
    val text: String?,
    val done: Boolean?
)

fun todoUpdateUseCase(
    tm: TransactionManager,
    selectTodo: SelectTodo,
    updateTodo: UpdateTodo
): TodoUpdateUseCase = { request ->
    tm.transactional(isolation = TransactionIsolation.SERIALIZABLE) {
        selectTodo(request.id)?.let { todo ->
            val updated = todo.apply(request)
            updateTodo(updated)
            updated.right()
        } ?: DomainError.TodoNotFound.left()
    }
}

private fun Todo.apply(request: TodoUpdateRequest): Todo {
    var result = this
    result = request.text?.let { result.copy(text = it.trim()) } ?: result
    result = request.done?.let { result.copy(done = it) } ?: result
    return result
}

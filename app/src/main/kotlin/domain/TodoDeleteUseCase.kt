package todoapp.domain

import todoapp.domain.TransactionIsolation.*

typealias TodoDeleteUseCase = suspend (TodoDeleteRequest) -> Unit

data class TodoDeleteRequest(val id: Int)

fun todoDeleteUseCase(
    transactional: Transactional,
    selectTodo: SelectTodo,
    deleteTodo: DeleteTodo,
    selectAllTodo: SelectAllTodo,
    updateTodoList: UpdateTodoList
): TodoDeleteUseCase = { request ->
    transactional(SERIALIZABLE) {
        selectTodo(request.id)?.let { todo ->
            deleteTodo(todo.id)
            val tail = selectAllTodo().filter { it.index > todo.index }
            val tailUpdated = tail.map { it.copy(index = it.index - 1) }
            updateTodoList(tailUpdated)
        }
    }
}
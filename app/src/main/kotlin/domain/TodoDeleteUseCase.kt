package todoapp.domain

typealias TodoDeleteUseCase = suspend (TodoDeleteRequest) -> Unit

data class TodoDeleteRequest(val id: Int)

fun todoDeleteUseCase(
    database: Database,
    selectTodo: SelectTodo,
    deleteTodo: DeleteTodo,
    selectAllTodo: SelectAllTodo,
    updateTodoList: UpdateTodoList
): TodoDeleteUseCase = { request ->
    database.transactional(isolation = TransactionIsolation.SERIALIZABLE) {
        selectTodo(request.id)?.let { todo ->
            deleteTodo(todo.id)
            val tail = selectAllTodo().filter { it.index > todo.index }
            val tailUpdated = tail.map { it.copy(index = it.index - 1) }
            updateTodoList(tailUpdated)
        }
    }
}
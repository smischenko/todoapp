package todoapp.domain

import todoapp.Repository
import todoapp.Transaction
import todoapp.TransactionIsolation

typealias TodoCreateUseCase = suspend (TodoCreateRequest) -> Todo

data class TodoCreateRequest(val text: String)

fun todoCreateUseCase(
    transaction: Transaction,
    repository: Repository
): TodoCreateUseCase = { request ->
    transaction(isolation = TransactionIsolation.SERIALIZABLE) {
        val todoCount = repository.selectTodoCount()
        val todo = Todo(
            id = 0, // set 0 as id is a trick to not break nonnullable id declaration
            text = request.text.trim(),
            done = false,
            index = todoCount
        )
        val id = repository.insertTodo(todo)
        todo.copy(id = id)
    }
}
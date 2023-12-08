package todoapp.domain

import todoapp.domain.TransactionIsolation.*

typealias TodoCreateUseCase = suspend (TodoCreateRequest) -> Todo

data class TodoCreateRequest(val text: String)

fun todoCreateUseCase(
    transactional: Transactional,
    selectTodoCount: SelectTodoCount,
    insertTodo: InsertTodo,
): TodoCreateUseCase = { request ->
    transactional(SERIALIZABLE) {
        val todoCount = selectTodoCount()
        val todo = Todo(
            id = 0, // set 0 as id is a trick to not break nonnullable id declaration
            text = request.text.trim(),
            done = false,
            index = todoCount
        )
        val id = insertTodo(todo)
        todo.copy(id = id)
    }
}
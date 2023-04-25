package todoapp.domain

import arrow.core.Either
import todoapp.domain.DomainError.UnexpectedError

typealias TodoCreateUseCase = suspend (TodoCreateRequest) -> Either<UnexpectedError, Todo>

data class TodoCreateRequest(val text: String)

fun todoCreateUseCase(
    tm: TransactionManager,
    selectTodoCount: SelectTodoCount,
    insertTodo: InsertTodo,
): TodoCreateUseCase = { request ->
    tm.transactional(isolation = TransactionIsolation.SERIALIZABLE) {
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
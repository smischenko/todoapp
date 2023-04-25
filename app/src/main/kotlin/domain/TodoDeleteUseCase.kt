package todoapp.domain

import arrow.core.Either
import todoapp.domain.DomainError.UnexpectedError

typealias TodoDeleteUseCase = suspend (TodoDeleteRequest) -> Either<UnexpectedError, Unit>

data class TodoDeleteRequest(val id: Int)

fun todoDeleteUseCase(
    tm: TransactionManager,
    selectTodo: SelectTodo,
    deleteTodo: DeleteTodo,
    selectAllTodo: SelectAllTodo,
    updateTodoList: UpdateTodoList
): TodoDeleteUseCase = { request ->
    tm.transactional(isolation = TransactionIsolation.SERIALIZABLE) {
        selectTodo(request.id)?.let { todo ->
            deleteTodo(todo.id)
            val tail = selectAllTodo().filter { it.index > todo.index }
            val tailUpdated = tail.map { it.copy(index = it.index - 1) }
            updateTodoList(tailUpdated)
        }
    }
}
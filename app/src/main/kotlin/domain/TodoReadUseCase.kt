package todoapp.domain

import todoapp.domain.TransactionIsolation.*

typealias TodoReadUseCase = suspend () -> List<Todo>

fun todoReadUseCase(transactional: Transactional, selectAllTodo: SelectAllTodo): TodoReadUseCase = {
    transactional(READ_COMMITTED, readOnly = true) {
        selectAllTodo()
    }
}
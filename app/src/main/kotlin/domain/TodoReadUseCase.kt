package todoapp.domain

import todoapp.domain.TransactionIsolation.*

typealias TodoReadUseCase = suspend () -> List<Todo>

fun todoReadUseCase(database: Database, selectAllTodo: SelectAllTodo): TodoReadUseCase = {
    database.transactional(READ_COMMITTED, readOnly = true) {
        selectAllTodo()
    }
}
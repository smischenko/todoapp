package todoapp.domain

typealias TodoReadUseCase = suspend () -> List<Todo>

fun todoReadUseCase(database: Database, selectAllTodo: SelectAllTodo): TodoReadUseCase = {
    database.transactional(isolation = TransactionIsolation.REPEATABLE_READ, readOnly = true) {
        selectAllTodo()
    }
}
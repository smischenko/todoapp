package todoapp.domain

typealias TodoReadUseCase = suspend () -> List<Todo>

fun todoReadUseCase(tm: TransactionManager, selectAllTodo: SelectAllTodo): TodoReadUseCase = {
    tm.transactional(isolation = TransactionIsolation.REPEATABLE_READ, readOnly = true) {
        selectAllTodo()
    }
}
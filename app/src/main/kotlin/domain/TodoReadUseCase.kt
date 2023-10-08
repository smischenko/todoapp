package todoapp.domain

typealias TodoReadUseCase = suspend () -> List<Todo>

fun todoReadUseCase(tm: TransactionManager, selectAllTodo: SelectAllTodo): TodoReadUseCase = {
    tm.transactional(isolation = TransactionIsolation.READ_COMMITTED, readOnly = true) {
        selectAllTodo()
    }
}
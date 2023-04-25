package todoapp.domain

import arrow.core.Either
import todoapp.domain.DomainError.UnexpectedError

typealias TodoReadUseCase = suspend () -> Either<UnexpectedError, List<Todo>>

fun todoReadUseCase(tm: TransactionManager, selectAllTodo: SelectAllTodo): TodoReadUseCase = {
    tm.transactional(isolation = TransactionIsolation.REPEATABLE_READ, readOnly = true) {
        selectAllTodo()
    }
}
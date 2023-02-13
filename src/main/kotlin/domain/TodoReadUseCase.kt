package todoapp.domain

import todoapp.Repository
import todoapp.Transaction
import todoapp.TransactionIsolation

typealias TodoReadUseCase = suspend () -> List<Todo>

fun todoReadUseCase(transaction: Transaction, repository: Repository): TodoReadUseCase = {
    transaction(isolation = TransactionIsolation.REPEATABLE_READ, readOnly = true) {
        repository.selectAllTodo()
    }
}
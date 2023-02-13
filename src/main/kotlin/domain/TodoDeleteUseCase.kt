package todoapp.domain

import todoapp.Repository
import todoapp.Transaction
import todoapp.TransactionIsolation

typealias TodoDeleteUseCase = suspend (TodoDeleteRequest) -> Unit

data class TodoDeleteRequest(val id: Int)

fun todoDeleteUseCase(transaction: Transaction, repository: Repository): TodoDeleteUseCase = { request ->
    transaction(isolation = TransactionIsolation.SERIALIZABLE) {
        repository.selectTodo(request.id)?.let { todo ->
            repository.deleteTodo(todo.id)
            val tail = repository.selectAllTodo().filter { it.index > todo.index }
            val tailUpdated = tail.map { it.copy(index = it.index - 1) }
            repository.updateTodo(tailUpdated)
        }
    }
}
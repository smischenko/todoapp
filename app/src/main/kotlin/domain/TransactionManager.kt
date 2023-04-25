package todoapp.domain

import arrow.core.Either

// Интерфейс транзакции aka Port Out

interface TransactionManager {
    suspend fun <T> transactional(
        isolation: TransactionIsolation,
        readOnly: Boolean = false,
        block: TransactionScope.() -> T
    ): Either<DomainError.UnexpectedError ,T>
}

enum class TransactionIsolation {
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}

interface TransactionScope
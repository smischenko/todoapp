package todoapp.domain

// Интерфейс транзакции aka Port Out

interface TransactionManager {
    suspend fun <T> transactional(
        isolation: TransactionIsolation,
        readOnly: Boolean = false,
        block: TransactionScope.() -> T
    ): T
}

enum class TransactionIsolation {
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}

interface TransactionScope
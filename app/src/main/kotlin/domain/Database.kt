package todoapp.domain

// Интерфейс запуска транзакции aka Port Out

interface Database {
    suspend fun <T> transactional(
        isolation: TransactionIsolation,
        readOnly: Boolean = false,
        block: suspend TransactionScope.() -> T
    ): T
}

enum class TransactionIsolation {
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}

interface TransactionScope
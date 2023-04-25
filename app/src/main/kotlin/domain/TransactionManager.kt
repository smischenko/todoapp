package todoapp.domain

// Интерфейс доступа к хранилищу aka Port Out

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
package todoapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import java.sql.Connection
import javax.sql.DataSource

// Responsible for transaction management: create then commit or rollback
class Transaction(private val dataSource: DataSource) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val connectionAcquisition = Dispatchers.IO.limitedParallelism(1)

    suspend operator fun <T> invoke(isolation: TransactionIsolation, readOnly: Boolean = false, block: suspend TransactionContext.() -> T): T =
        withContext(connectionAcquisition) { dataSource.connection }.use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = isolation.toJdbcValue()
            connection.isReadOnly = readOnly
            try {
                TransactionContext(connection).block().also { connection.commit() }
            } catch (t: Throwable) {
                connection.rollback()
                throw t
            }
        }
}

enum class TransactionIsolation {
    READ_COMMITTED,
    REPEATABLE_READ,
    SERIALIZABLE
}

private fun TransactionIsolation.toJdbcValue() =
    when (this) {
        TransactionIsolation.READ_COMMITTED -> Connection.TRANSACTION_READ_COMMITTED
        TransactionIsolation.REPEATABLE_READ -> Connection.TRANSACTION_REPEATABLE_READ
        TransactionIsolation.SERIALIZABLE -> Connection.TRANSACTION_SERIALIZABLE
    }

data class TransactionContext(val connection: Connection)

val TransactionContext.jooq
    get() = DSL.using(this.connection, POSTGRES)

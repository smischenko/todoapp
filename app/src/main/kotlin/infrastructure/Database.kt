package todoapp.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import todoapp.domain.TransactionIsolation
import todoapp.domain.Database
import todoapp.domain.TransactionScope
import java.sql.Connection
import javax.sql.DataSource

fun database(dataSource: DataSource): Database =
    object : Database {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val connectionAcquisition = Dispatchers.IO.limitedParallelism(1)

        override suspend fun <T> transactional(
            isolation: TransactionIsolation,
            readOnly: Boolean,
            block: suspend TransactionScope.() -> T
        ): T =
            withContext(connectionAcquisition) { dataSource.connection }.use { connection ->
                connection.autoCommit = false
                connection.transactionIsolation = isolation.toJdbcValue()
                connection.isReadOnly = readOnly
                try {
                    JdbcTransactionScope(connection).block()
                        .also { connection.commit() }
                } catch (t: Throwable) {
                    connection.rollback()
                    throw t
                }
            }
    }

private fun TransactionIsolation.toJdbcValue() =
    when (this) {
        TransactionIsolation.READ_COMMITTED -> Connection.TRANSACTION_READ_COMMITTED
        TransactionIsolation.REPEATABLE_READ -> Connection.TRANSACTION_REPEATABLE_READ
        TransactionIsolation.SERIALIZABLE -> Connection.TRANSACTION_SERIALIZABLE
    }

data class JdbcTransactionScope(val connection: Connection) : TransactionScope

val TransactionScope.jooq
    get() = DSL.using((this as JdbcTransactionScope).connection, SQLDialect.POSTGRES)
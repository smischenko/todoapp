package todoapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import java.sql.Connection
import javax.sql.DataSource

@OptIn(ExperimentalCoroutinesApi::class)
private val connectionAcquisition = Dispatchers.IO.limitedParallelism(1)

suspend fun <T> DataSource.transactional(transactionIsolation: Int, readOnly: Boolean = false, block: suspend Connection.() -> T): T =
    withContext(connectionAcquisition) { connection }.use { connection ->
        connection.autoCommit = false
        connection.transactionIsolation = transactionIsolation
        connection.isReadOnly = readOnly
        try {
            connection.block().also { connection.commit() }
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        }
    }

val Connection.jooq
    get() = DSL.using(this, POSTGRES)

package todoapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.sequence
import org.jooq.impl.DSL.table
import org.jooq.impl.SQLDataType.BOOLEAN
import org.jooq.impl.SQLDataType.CLOB
import org.jooq.impl.SQLDataType.INTEGER
import java.sql.Connection
import javax.sql.DataSource

val TODO = table(name("todo"))

val ID = field(name("id"), INTEGER)
val TEXT = field(name("text"), CLOB)
val DONE = field(name("done"), BOOLEAN)
val INDEX = field(name("index"), INTEGER)

val TODO_ID_SEQ = sequence(name("todo_id_seq"), INTEGER)

suspend fun <T> DataSource.transactional(transactionIsolation: Int, readOnly: Boolean = false, block: suspend Connection.() -> T): T =
    withContext(Dispatchers.IO) { connection }.use { connection ->
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

package todoapp.infrastructure

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.jooq.Record
import todoapp.domain.*

fun selectTodoCount(): SelectTodoCount = {
    withContext(IO) {
        jooq.resultQuery("SELECT count(*) FROM todo")
            .fetchSingle()
            .get(0, Int::class.java)
    }
}

fun insertTodo(): InsertTodo = { todo ->
    withContext(IO) {
        val query = """
            INSERT INTO todo(text, done, index)
            VALUES (?, ?, ?)
            RETURNING id
        """.trimIndent()
        jooq.resultQuery(query, todo.text, todo.done, todo.index)
            .fetchSingle().get(0, Int::class.java)
    }
}

fun selectAllTodo(): SelectAllTodo = {
    withContext(IO) {
        val query = """
            SELECT id, text, done, index
            FROM todo
            ORDER BY index
        """.trimIndent()
        jooq.resultQuery(query).fetch { it.toTodo() }
    }
}

fun selectTodo(): SelectTodo = { id ->
    withContext(IO) {
        val query = """
            SELECT id, text, done, index
            FROM todo
            WHERE id = ?
        """.trimIndent()
        jooq.resultQuery(query, id).fetchOne { it.toTodo() }
    }
}

fun updateTodo(): UpdateTodo = { todo ->
    withContext(IO) {
        val query = """
            UPDATE todo
            SET text = ?, done = ?, index = ?
            WHERE id = ?
        """.trimIndent()
        jooq.query(query, todo.text, todo.done, todo.index, todo.id).execute()
    }
}

fun updateTodoList(): UpdateTodoList = { todos ->
    withContext(IO) {
        if (todos.isNotEmpty()) {
            val query = """
                UPDATE todo
                SET text = ?, done = ?, index = ?
                WHERE id = ?
            """.trimIndent()
            val batch = jooq.batch(query)
            todos.forEach { todo ->
                batch.bind(todo.text, todo.done, todo.index, todo.id)
            }
            batch.execute()
        }
    }
}

fun deleteTodo(): DeleteTodo = { id ->
    withContext(IO) {
        jooq.query("DELETE FROM todo WHERE id = ?", id).execute()
    }
}

private fun Record.toTodo() =
    Todo(
        id = this.get("id", Int::class.java),
        text = this.get("text", String::class.java),
        done = this.get("done", Boolean::class.java),
        index = this.get("index", Int::class.java),
    )
package todoapp.infrastructure

import org.jooq.Record
import todoapp.domain.*
import todoapp.jooq.tables.references.TODO

fun selectTodoCount(): SelectTodoCount = {
    jooq.selectCount().from(TODO).fetchSingle().value1()
}

fun insertTodo(): InsertTodo = { todo ->
    jooq.insertInto(TODO)
        .set(TODO.TEXT, todo.text)
        .set(TODO.DONE, todo.done)
        .set(TODO.INDEX, todo.index)
        .returning(TODO.ID)
        .execute()
}

fun selectAllTodo(): SelectAllTodo = {
    jooq.select()
        .from(TODO)
        .orderBy(TODO.INDEX)
        .fetch { it.toTodo() }
}

fun selectTodo(): SelectTodo = {id ->
    jooq.select()
        .from(TODO)
        .where(TODO.ID.eq(id))
        .fetchOne { it.toTodo() }
}

fun updateTodo(): UpdateTodo = { todo ->
    jooq.update(TODO)
        .set(TODO.TEXT, todo.text)
        .set(TODO.DONE, todo.done)
        .set(TODO.INDEX, todo.index)
        .where(TODO.ID.eq(todo.id))
        .execute()

}

fun updateTodoList(): UpdateTodoList = { todos ->
    if (todos.isNotEmpty()) {
        val batch = jooq.batch(
            jooq.update(TODO)
                .set(TODO.TEXT, null as String?)
                .set(TODO.DONE, null as Boolean?)
                .set(TODO.INDEX, null as Int?)
                .where(TODO.ID.eq(null as Int?))
        )
        todos.forEach { todo ->
            batch.bind(todo.text, todo.done, todo.index, todo.id)
        }
        batch.execute()
    }
}

fun deleteTodo(): DeleteTodo = { id ->
    jooq.deleteFrom(TODO)
        .where(TODO.ID.eq(id))
        .execute()
}

private fun Record.toTodo() =
    Todo(
        id = this[TODO.ID]!!,
        text = this[TODO.TEXT]!!,
        done = this[TODO.DONE]!!,
        index = this[TODO.INDEX]!!
    )
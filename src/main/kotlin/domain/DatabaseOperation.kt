package todoapp.domain

// Операции с хранилищем aka Port Out

typealias InsertTodo = TransactionScope.(Todo) -> Int
typealias SelectTodoCount = TransactionScope.() -> Int
typealias SelectAllTodo = TransactionScope.() -> List<Todo>
typealias SelectTodo = TransactionScope.(Int) -> Todo?
typealias UpdateTodo = TransactionScope.(Todo) -> Unit
typealias UpdateTodoList = TransactionScope.(List<Todo>) -> Unit
typealias DeleteTodo = TransactionScope.(Int) -> Unit
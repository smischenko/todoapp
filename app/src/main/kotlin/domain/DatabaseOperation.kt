package todoapp.domain

// Операции с хранилищем aka Port Out

typealias InsertTodo = suspend TransactionScope.(Todo) -> Int
typealias SelectTodoCount = suspend TransactionScope.() -> Int
typealias SelectAllTodo = suspend TransactionScope.() -> List<Todo>
typealias SelectTodo = suspend TransactionScope.(Int) -> Todo?
typealias UpdateTodo = suspend TransactionScope.(Todo) -> Unit
typealias UpdateTodoList = suspend TransactionScope.(List<Todo>) -> Unit
typealias DeleteTodo = suspend TransactionScope.(Int) -> Unit
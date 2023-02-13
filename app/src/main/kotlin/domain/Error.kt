package todoapp.domain

sealed class Error {
    data class RequestReceiveError(val message: String) : Error()
    object TodoNotFound : Error()
}
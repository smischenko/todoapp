package todoapp.domain

sealed class DomainError {
    object UnexpectedError : DomainError()
    object TodoNotFound : DomainError()
}
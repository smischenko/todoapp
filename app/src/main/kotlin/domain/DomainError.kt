package todoapp.domain

sealed class DomainError {
    object TodoNotFound : DomainError()
}
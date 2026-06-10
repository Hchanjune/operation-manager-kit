package io.github.hchanjune.omk.core.contants

enum class OperationOutcome {
    SUCCESS,
    UNAUTHENTICATED,
    FORBIDDEN,
    CLIENT_ERROR,
    SERVER_ERROR;

    companion object {
        fun fromStatusCode(statusCode: Int): OperationOutcome = when {
            statusCode == 401 -> UNAUTHENTICATED
            statusCode == 403 -> FORBIDDEN
            statusCode in 400..499 -> CLIENT_ERROR
            statusCode >= 500 -> SERVER_ERROR
            else -> SUCCESS
        }
    }
}

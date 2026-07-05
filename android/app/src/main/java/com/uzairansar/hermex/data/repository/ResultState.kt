package com.uzairansar.hermex.data.repository

sealed interface ResultState<out T> {
    data object Loading : ResultState<Nothing>
    data class Data<T>(val value: T, val fromCache: Boolean = false) : ResultState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : ResultState<Nothing>
}

fun Throwable.userMessage(): String = message ?: "Something went wrong."

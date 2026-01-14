package com.termux.app.core.api

/**
 * A sealed class representing the result of an operation.
 * Provides a type-safe alternative to nullable returns and exception-based error handling.
 *
 * Usage:
 * ```kotlin
 * when (val result = performOperation()) {
 *     is Result.Success -> handleSuccess(result.data)
 *     is Result.Error -> handleError(result.error)
 *     is Result.Loading -> showLoading()
 * }
 * ```
 */
sealed class Result<out T, out E : TermuxError> {
    
    /**
     * Represents a successful operation with data.
     */
    data class Success<T>(val data: T) : Result<T, Nothing>()
    
    /**
     * Represents a failed operation with a typed error.
     */
    data class Error<E : TermuxError>(val error: E) : Result<Nothing, E>()
    
    /**
     * Represents an operation in progress.
     */
    data object Loading : Result<Nothing, Nothing>()
    
    /**
     * Returns true if this is a Success result.
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns true if this is an Error result.
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Returns true if this is a Loading result.
     */
    val isLoading: Boolean get() = this is Loading
    
    /**
     * Returns the data if Success, null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
    
    /**
     * Returns the data if Success, throws the error otherwise.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw error.toException()
        is Loading -> throw IllegalStateException("Operation still loading")
    }
    
    /**
     * Returns the data if Success, default value otherwise.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }
    
    /**
     * Returns the error if Error, null otherwise.
     */
    fun errorOrNull(): E? = when (this) {
        is Error -> error
        else -> null
    }
    
    /**
     * Maps the success data to another type.
     */
    inline fun <R> map(transform: (T) -> R): Result<R, E> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(error)
        is Loading -> Loading
    }
    
    /**
     * Maps the error to another error type.
     */
    inline fun <F : TermuxError> mapError(transform: (E) -> F): Result<T, F> = when (this) {
        is Success -> Success(data)
        is Error -> Error(transform(error))
        is Loading -> Loading
    }
    
    /**
     * Flat maps the success data.
     */
    inline fun <R> flatMap(transform: (T) -> Result<R, @UnsafeVariance E>): Result<R, E> = when (this) {
        is Success -> transform(data)
        is Error -> Error(error)
        is Loading -> Loading
    }
    
    /**
     * Executes action if Success.
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T, E> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Executes action if Error.
     */
    inline fun onError(action: (E) -> Unit): Result<T, E> {
        if (this is Error) action(error)
        return this
    }
    
    /**
     * Executes action if Loading.
     */
    inline fun onLoading(action: () -> Unit): Result<T, E> {
        if (this is Loading) action()
        return this
    }
    
    companion object {
        /**
         * Creates a Success result.
         */
        fun <T> success(data: T): Result<T, Nothing> = Success(data)
        
        /**
         * Creates an Error result.
         */
        fun <E : TermuxError> error(error: E): Result<Nothing, E> = Error(error)
        
        /**
         * Creates a Loading result.
         */
        fun loading(): Result<Nothing, Nothing> = Loading
        
        /**
         * Wraps a block in try-catch, returning Success or Error.
         */
        inline fun <T> runCatching(block: () -> T): Result<T, SystemError> = try {
            Success(block())
        } catch (e: Exception) {
            Error(SystemError.Exception(e))
        }
    }
}

/**
 * Type alias for results with no meaningful data.
 */
typealias UnitResult<E> = Result<Unit, E>

/**
 * Type alias for results that can have any TermuxError.
 */
typealias TermuxResult<T> = Result<T, TermuxError>

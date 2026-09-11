package com.streaming.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streaming.platform.authentication.AuthState
import com.streaming.platform.authentication.SessionManager
import com.streaming.platform.core.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val sessionManager: SessionManager) : ViewModel() {
    val authState: StateFlow<AuthState> = sessionManager.state

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    init {
        viewModelScope.launch { sessionManager.restoreSession() }
    }

    fun login(email: String, password: String) {
        _formError.value = null
        _isSubmitting.value = true
        viewModelScope.launch {
            sessionManager.login(email, password).onFailure { _formError.value = it.loginFormMessage() }
            _isSubmitting.value = false
        }
    }

    fun register(email: String, password: String, displayName: String) {
        _formError.value = null
        _isSubmitting.value = true
        viewModelScope.launch {
            sessionManager.register(email, password, displayName).onFailure { _formError.value = it.loginFormMessage() }
            _isSubmitting.value = false
        }
    }

    fun logout() {
        viewModelScope.launch { sessionManager.logout() }
    }
}

/**
 * Turns an AppErrorException (or any other failure) into UI-safe copy — never a raw stack trace.
 * AppError.Unauthorized here means a session that ApiClient's own refresh-and-retry couldn't save
 * (the refresh token itself is invalid/expired), not bad credentials — that specific wording is
 * only correct at the login/register call sites, see [loginFormMessage].
 */
fun Throwable.readableMessage(): String {
    val appError = (this as? com.streaming.platform.network.AppErrorException)?.error
    return when (appError) {
        is AppError.Unauthorized -> "Your session has expired. Please sign in again."
        is AppError.Validation -> appError.message
        is AppError.Conflict -> appError.message
        is AppError.NotFound -> appError.message
        is AppError.Network -> "Can't reach the server. Check your connection."
        else -> message ?: "Something went wrong. Please try again."
    }
}

/** Same mapping as [readableMessage], except Unauthorized means what it says on a login/register form. */
fun Throwable.loginFormMessage(): String {
    val appError = (this as? com.streaming.platform.network.AppErrorException)?.error
    return if (appError is AppError.Unauthorized) "Incorrect email or password." else readableMessage()
}

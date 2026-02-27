package data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tribbae_session", Context.MODE_PRIVATE)
    
    private val _isLoggedIn = MutableStateFlow(hasToken())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    
    private val _userId = MutableStateFlow(getUserId())
    val userId: StateFlow<String?> = _userId.asStateFlow()
    
    private val _displayName = MutableStateFlow(getDisplayName())
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    fun saveSession(userId: String, token: String, displayName: String) {
        prefs.edit().apply {
            putString("user_id", userId)
            putString("token", token)
            putString("display_name", displayName)
            apply()
        }
        _isLoggedIn.value = true
        _userId.value = userId
        _displayName.value = displayName
    }

    fun getToken(): String? {
        return prefs.getString("token", null)
    }

    fun getUserId(): String? {
        return prefs.getString("user_id", null)
    }

    fun getDisplayName(): String? {
        return prefs.getString("display_name", null)
    }

    fun hasToken(): Boolean {
        return !getToken().isNullOrEmpty()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _isLoggedIn.value = false
        _userId.value = null
        _displayName.value = null
    }
}

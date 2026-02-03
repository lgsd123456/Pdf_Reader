package com.example.pdfreader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    val darkMode: StateFlow<Boolean> = _darkMode

    private val _zoom = MutableStateFlow(prefs.getFloat(KEY_ZOOM, 1.0f).coerceIn(0.5f, 3.0f))
    val zoom: StateFlow<Float> = _zoom

    private val _fullScreen = MutableStateFlow(prefs.getBoolean(KEY_FULL_SCREEN, false))
    val fullScreen: StateFlow<Boolean> = _fullScreen

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _darkMode.value = enabled
    }

    fun toggleDarkMode() {
        setDarkMode(!_darkMode.value)
    }

    fun setZoom(value: Float) {
        val clamped = value.coerceIn(0.5f, 3.0f)
        prefs.edit().putFloat(KEY_ZOOM, clamped).apply()
        _zoom.value = clamped
    }

    fun setFullScreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FULL_SCREEN, enabled).apply()
        _fullScreen.value = enabled
    }

    fun toggleFullScreen() {
        setFullScreen(!_fullScreen.value)
    }

    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_ZOOM = "zoom"
        private const val KEY_FULL_SCREEN = "full_screen"
    }
}

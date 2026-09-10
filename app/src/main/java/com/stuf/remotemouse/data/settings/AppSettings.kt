package com.stuf.remotemouse.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("remote_mouse_settings", Context.MODE_PRIVATE)

    private val _touchpadSensitivity = MutableStateFlow(prefs.getFloat(KEY_SENSITIVITY, 1.2f))
    val touchpadSensitivity: StateFlow<Float> = _touchpadSensitivity.asStateFlow()

    fun setTouchpadSensitivity(value: Float) {
        val clamped = value.coerceIn(0.3f, 3f)
        prefs.edit().putFloat(KEY_SENSITIVITY, clamped).apply()
        _touchpadSensitivity.value = clamped
    }

    companion object {
        private const val KEY_SENSITIVITY = "touchpad_sensitivity"
    }
}

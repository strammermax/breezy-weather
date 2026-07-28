package com.liveweatherwallpaperapp.domain.settings

import androidx.core.content.edit

import android.content.Context

/** User-unlocked tester UI; intentionally separate from the app's debuggable-build mode. */
class TesterModeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    val isUnlocked: Boolean
        get() = preferences.getBoolean(KEY_UNLOCKED, false)

    var isEnabled: Boolean
        get() = isUnlocked && preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferences.edit {putBoolean(KEY_ENABLED, value)}
        }

    fun unlock() {
        preferences.edit {
                putBoolean(KEY_UNLOCKED, true)
                .putBoolean(KEY_ENABLED, true)
            }
    }

    fun lock() {
        preferences.edit {
                putBoolean(KEY_UNLOCKED, false)
                .putBoolean(KEY_ENABLED, false)
            }
    }

    companion object {
        private const val PREFERENCES = "tester_mode"
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_ENABLED = "enabled"
    }
}
